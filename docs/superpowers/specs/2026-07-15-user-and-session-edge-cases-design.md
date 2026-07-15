# 用户与会话边界问题修复 — 设计说明

日期：2026-07-15
状态：已与用户确认设计，待实现

## 背景 / Background

管理员后台在用户管理、权限调整等场景下存在若干边界缺陷，根因集中在两处：

1. **会话快照过期**：`LoginSession` 在登录时把 `storageSpaces`、`role` 固化为快照。管理员改了配置（`filebox.yml` 会热加载），但已登录会话仍持旧快照 —— 刷新页面也不生效，只能重新登录。
2. **缺少自我/收尾保护**：管理员可以删除自己、删除/降级最后一个管理员、改了别人密码但对方旧会话仍可用、被删除的用户仍保持登录。

文件访问的真正鉴权点 `FileBoxController.canUserAccessStorageSpace`（仅对 `USER` 角色校验）以及 `/api/user` 接口都直接读 `session.getStorageSpaces()` 这个旧快照；而 `AuthService.getCurrentUser` 其实已从配置重新派生，但 `/api/user` 未走该路径（历史不一致）。

## 设计原则 / Guiding principle

按“变更的敏感度”区分处理方式：

- **安全敏感变更**（角色、密码、删除、改名）→ **强制对应会话下线**，要求重新登录。简单、无残留、无提权/降级悬空。
- **存储空间权限变更** → **不打扰**，下次刷新页面平滑生效（配置即真相，按需重派生）。

## 范围：六项变更 / Scope (6 items)

### 1. 删除用户的两道防线（仅后端）

- **禁止删自己**：`AdminController.deleteUser` 增加 `HttpServletRequest` 参数，从 `request.getAttribute("session")` 取调用方 `LoginSession`；若目标 `username` 与调用方用户名相等（`equals`，区分大小写，与全局一致），直接返回 `{success:false, error:"不能删除当前登录的账号"}`，不调用 service。
- **禁止删最后一个管理员**：`UserService.deleteUser` 在定位到目标后，若目标 role 为 `ADMIN` 且配置中 `ADMIN` 数量 == 1，抛 `IllegalStateException("系统至少需要保留一个管理员")`；`AdminController.deleteUser` 捕获并以 `e.getMessage()` 作为 `error` 返回（与同文件 `createStorage`/`updateStorage` 的异常透出模式一致）。
- 前端不动：`users.html` 已展示后端 `result.error`（第 372 行），原有 `username === 'admin'` 前端硬编码守卫保留（冗余但无害）。

### 2. 初始/重置密码字符集

- `SecureTokenGenerator` 新增 `generateAlphanumeric(int length)`：从 `A–Z a–z 0–9`（62 符号）用现有 `SecureRandom`，逐字符 `secureRandom.nextInt(62)` 取索引拼接。（62 上的模偏置对 22 字符密码可忽略，不再做拒绝采样。）
- `FileBoxConfigStore.generateAdminPassword()` 改为 `SecureTokenGenerator.generateAlphanumeric(22)`。
- 长度保持 22：字母数字约 131 bit 熵，与现状 Base64-URL 版本安全相当，仅去掉 `-`/`_`。
- 一处改动同时覆盖首启引导（第 99 行）与 `reset-admin-password` 维护命令（第 118 行），二者都调用 `generateAdminPassword()`。
- 双语注释，与文件风格一致。

### 3. “无可用存储空间”登录后提示（前端）

- 位置：`index.js` 中已有的 `/api/user` `.then(async data => ...)` 回调内，在算出 `spaceList` 并渲染页头用户名/角色之后。
- 条件：`!window.isAnonymous && data.role !== 'admin' && spaceList.length === 0`。匿名用户登录时若无匿名空间本就失败；ADMIN 理论上拥有全部空间，空集属另一类（系统无任何空间），不在本提示语义内。
- 动作：`await Notify.alert({ title:'暂无可用存储空间', content:'管理员尚未为您分配任何存储空间的访问权限，您暂时无法正常使用本系统，请联系管理员处理。', okText:'我知道了' })`。
- 频次：**每浏览器会话一次**，用 `sessionStorage` 标志（如 `filebox_noSpaces_notified`），避免刷新反复打扰。简单备选是每次加载都弹（用户已选“仅靠后端拦截”风格，这里按会话一次更克制）。

### 4. 存储空间权限变更在“刷新”时生效（后端，强制下线的反面）

- 在 `AuthService` 新增 `refreshStorageSpaces(LoginSession session)`：加载配置，找到对应用户，用既有 `getStorageSpacesForUser(userConfig, config)` 重派生 `storageSpaces`，`session.setStorageSpaces(...)`；若 `currentStorageSpace` 不在新集合内，则夹紧到新集合首个（集合为空则置 `null`）。
- `AuthController.getLegacyUserInfo`（即 `/api/user`）在读取 session 各字段前先调用 `authService.refreshStorageSpaces(session)`，使每次页面加载都把会话空间刷成最新。
- 效果：UI 显示最新空间；后续文件操作读 `session.getStorageSpaces()` 自然也是最新，`canUserAccessStorageSpace` / `switchStorageSpace` 无需改动。
- 与第 3 项联动：刷新后若 `spaceList` 为空 → 触发“无空间”提示。
- 注意：仅作用于 `/api/user`（index.js 实际调用者）；`/api/auth/user`（admin-common.js 用，只显示用户名/校验 ADMIN）不在本特性范围，如需一致可复用同一 helper（可选）。

### 5. 安全敏感变更 → 强制对应会话下线（后端）

- `AuthService` 新增 `invalidateSessionsForUser(String username)`：从 `sessions` 移除所有该用户名的会话。
- 触发点（均在 `AdminController`，且仅在 `userService.xxx` 成功后）：
  - **改密码**：`updateUser` 请求中 `password` 非空 → 下线该用户。
  - **改角色**：调用前用 `userService.getUser(username).getRole()` 取旧角色，与新角色不等 → 下线该用户（升降级一视同仁）。
  - **改名**：新用户名非空且与旧名不同 → 下线**旧用户名**的会话。
  - **删用户**：`deleteUser` 成功 → 下线该用户（被删用户不应继续在线，顺带修补既有缺口）。
- 规则要点：仅当**密码或角色实际变更**（或删除/改名）才下线；只调整存储空间**不**下线，避免管理员编辑空间时把自己/别人踢下线。
- 自助改密 `/api/auth/change-password` **不**受影响（用户知道当前密码，合法自助改密应保持登录）。
- 管理员改自己的密码/角色也会下线自己 —— 符合预期（改自己密码后重新登录是常见行为）。

### 6. “最后一个管理员”角色降级防线（后端，第 1 项的类比）

- `UserService.updateUser` 在改动目标前：若目标当前 role 为 `ADMIN`、新 role 非 `ADMIN`、且配置中 `ADMIN` 数量 == 1 → 抛 `IllegalStateException("系统至少需要保留一个管理员")`。
- `AdminController.updateUser` 需 try/catch 该异常并以 `e.getMessage()` 作为 `error` 返回（当前未捕获，需补）。
- 这样管理员无法把最后一个 ADMIN 降级（含把自己降级）从而导致后台全员被锁，与删除防线对称。

## 涉及文件 / Files touched

| 文件 | 改动 |
|---|---|
| `controller/AdminController.java` | `deleteUser` 加 self 守卫 + 异常捕获；`updateUser` 加下线触发与异常捕获 |
| `service/UserService.java` | `deleteUser` 加 last-admin 守卫；`updateUser` 加 last-admin-role 守卫 |
| `service/AuthService.java` | 新增 `invalidateSessionsForUser`、`refreshStorageSpaces` |
| `controller/AuthController.java` | `/api/user` 调用 `refreshStorageSpaces` |
| `security/SecureTokenGenerator.java` | 新增 `generateAlphanumeric` |
| `service/FileBoxConfigStore.java` | `generateAdminPassword` 改用字母数字 |
| `static/js/index.js` | 无空间提示（`Notify.alert` + `sessionStorage`） |

## 测试 / Testing

- `UserService`：last-admin 删除/降级两类守卫的单测（删非最后 admin 成功；删最后 admin 抛异常；存在第二个 admin 时删除成功；降级最后 admin 抛异常；降级非最后 admin 成功）。
- `SecureTokenGenerator.generateAlphanumeric`：长度正确、字符集仅为字母数字。
- `AuthService.refreshStorageSpaces`：新增空间后重派生包含之；移除当前空间后夹紧到首个；清空后 current 置 null。
- 控制器层 self-删除守卫、`/api/user` 刷新、下线触发属集成行为，单测成本较高，以“服务层单测 + 手动验证”覆盖。

现有测试：JUnit5 + AssertJ（`FileCatalogServiceTest`、`FileBoxControllerSortTest`）。新增测试沿用该风格。

## 不在本次范围 / Out of scope

- `/api/auth/user`（admin-common 用）的空间刷新（可选后续）。
- 手工编辑 `filebox.yml` 的实时生效（配置热加载已覆盖读取；会话空间在下次 `/api/user` 刷新时同步）。
- 自助改密下线（刻意保留登录）。
