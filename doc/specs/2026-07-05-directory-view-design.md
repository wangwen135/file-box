# 文件目录浏览视图 — 设计方案

日期: 2026-07-05 ｜ 模块: `FileBoxController` + `index.html` + `FileUtils`

## 目标

在现有"最近上传(按时间平铺)"视图之外,新增一种**按目录浏览**的视图:用户可像文件管理器一样,在文件夹之间钻取浏览、新建文件夹、上传到当前文件夹、重命名、移动、删除。同时改进上传文件名处理:合规文件名原样保留,不再被改写。

## 关键决策(已与用户确认)

1. **自定义文件夹**(与 年/月 系统目录共用同一棵存储树),支持任意层级嵌套。
2. **上传目标 = 当前文件夹**;在根目录上传仍按 年/月 自动归档(保留现有行为)。
3. **交互形态 = 钻取式 + 面包屑**。
4. **下载路由**:新增 `GET /api/file?path=<relpath>`,旧的 `/uploads/{year}/{month}/{filename}` **不动**(降低风险)。
5. **结构类操作权限收紧为 ADMIN**:新建/删除文件夹、重命名、移动 → 仅 ADMIN。(删文件维持现状 ADMIN/MANAGER;上传维持现状:登录且有存储空间访问)
6. **上传文件名**:合规校验工具,合规→原名原样保留,重复→加序号。

## 数据模型

存储根 `<storage>/` 是单一目录树:
- 系统目录:上传年/月,如 `2026/07/`(根目录上传时自动产生)。
- 自定义文件夹:用户创建,如 `项目A/`、`项目A/子/`。
所有 `path` 参数都是**相对当前存储空间根**的相对路径;服务端解析后必须满足 `target.startsWith(basePath)`。

## 后端改动(`FileBoxController`)

新端点(均做路径防穿越 `normalize + startsWith(basePath)`):

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/list_dir` | GET | 登录 | `?path=<relpath>`(空=根);返回 `{folders:[{name}], files:[record]}`。文件 record 复用 `buildRecord` 思路,url 用 `/api/file?path=<encodedRelPath>` |
| `/create_folder` | POST | ADMIN | body `{path}`;`Files.createDirectories`;已存在返回 409 |
| `/rename_item` | POST | ADMIN | body `{path, newName}`;重命名文件或文件夹(叶子)。`newName` 走合规校验;目标已存在返回 409 |
| `/move_item` | POST | ADMIN | body `{src, destDir}`;移动文件/文件夹到 `destDir` 下,冲突返回 409;禁止把目录移进自己子树 |
| `/delete_folder` | DELETE | ADMIN | `?path=<relpath>`;递归删除 |
| `/api/file` | GET | 登录 | `?path=<relpath>`;下载任意深度路径的文件。**抽 `serveResolved(Path,req,resp)` 复用**(保留 Range/大文件流式/错误处理),旧 `/uploads/{y}/{m}/{fn}` 调同一 helper,行为不变 |

改动端点:

- `POST /upload_file`:增加可选 `targetFolder`(relpath)。有 → 落到 `<storage>/<targetFolder>/`;无 → 年/月(默认)。文件名走新的合规工具。
- `DELETE /delete_file`:已按 relpath 解析,**无需改动**;前端传 relpath 即可(两种视图通用)。
- `buildRecord`:增加一个目录视图用的变体 / 入参 `url`,由调用方决定 url 形态(旧视图 `/uploads/y/m/fn`,目录视图 `/api/file?path=...`)。year/month 字段对目录视图文件可省略(前端不读)。

## 文件名合规工具(`FileUtils`,新增;替换上传处的 `safeUnicodeFilename` 调用)

```java
// 合规判定:无需任何改写即可安全使用
public static boolean isFilenameCompliant(String name):
    非空 && 是纯文件名(Paths.get(name).getFileName().equals(name))
    && 不含 '/' '\' '\0' && 不含控制字符
    && 不是纯点(.. / . / 纯多点)
    && UTF-8 字节长度 ≤ 255

// 上传准备:合规→原样返回;不合规→最小清理(剥 basename、去控制字符、纯点回退、超长截断保扩展名)
// 不做 NFC、不压缩空白 —— 合规名逐字节保留
public static String prepareUploadFilename(String original):
    ... 合规直接返回 original;否则最小清理 ...
```

上传流程:`filename = prepareUploadFilename(original)` → 若与目录下已有文件重名,追加 ` (1)/(2)...` 序号(沿用现有 dedup 逻辑)。

权限常量、文件名长度上限等放 `AppConstants`(遵循"用常量不用魔法数字")。

## 前端改动(`index.html`)

- 顶部视图切换:`[最近上传]  [📁 目录浏览]`(两个 tab,切换显示对应区块)。最近上传视图**完全不动**。
- 目录视图区块:
  - 面包屑:`🏠 根目录 / 项目A / 子 /`(每段可点跳回)。
  - 工具栏:`[＋ 新建文件夹]` `[📤 上传到当前]`(ADMIN 才显示新建/重命名/移动/删除按钮)。
  - 内容区:子文件夹卡片(📁,可进入)+ 文件卡片(复用现有 `createFileElement`,带预览/ADMIN 删除)。
  - 每个卡片右下角(ADMIN)加 `✏ 重命名` `↔ 移动` 入口;文件夹加 `🗑 删除`。
- 状态:`dirPath` 数组(当前层级);进入文件夹 push,面包屑 splice;切 tab 不丢最近上传的状态。
- 上传:目录视图下 `FormData` 多带 `targetFolder = dirPath.join('/')`(根目录不带 → 后端走 年/月)。
- 重命名/移动:简单 prompt/Notify.confirm 收集 newName/目标文件夹,POST 对应端点,成功后刷新当前目录。
- 空目录/加载/错误态沿用现有样式与 `Notify`。

## 本次范围(YAGNI,不做)

按文件夹的细粒度授权、文件夹配额、拖拽移动、多选批量操作、目录下文件的时间筛选。→ 后续迭代。

## 风险与对策

- **最大风险**:服务端路径处理(下载/列目录/移动)。对策:每个新端点 `normalize + startsWith(basePath)`,且禁止 `..` 越界;移动时额外校验"不能把目录移进自身子树"。
- **下载复用**:抽 `serveResolved` helper,旧路由调用同一 helper,行为不变;用 curl 同时验证「年/月文件」和「自定义文件夹文件」下载(含 Range 206)。
- **文件名**:合规工具要保证合规名 100% 原样(不引入 NFC 等隐性改写),用单元式断言验证。

## 自测清单

1. 视图切换不破坏最近上传。
2. ADMIN 建文件夹(含多级)→ 上传到该文件夹 → 目录视图可见 → `/api/file?path=` 下载正常(含 Range)。
3. 上传"合规中文名.png"→ 落盘名与上传名**逐字节一致**;再传同名 → 变"合规中文名 (1).png"。
4. ADMIN 重命名 / 移动文件与文件夹;非 ADMIN 被拒。
5. 路径穿越(含 `..`)被所有新端点拒绝。
6. 最近上传视图文件下载(旧路由)不受影响。
