# 用户与会话边界问题修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix six admin/user-management edge cases: delete-self & last-admin guards, alphanumeric first-launch password, no-storage-space login prompt, storage-space permission changes taking effect on refresh, and forcing users offline on password/role/delete/rename changes.

**Architecture:** Service-layer invariants (last-admin guards, password generator, session refresh, session invalidation) are covered by pure JUnit5 + AssertJ unit tests that wire a real `ConfigService` to a temp YAML file via `ReflectionTestUtils`. Controller wiring and the frontend popup are implemented then verified manually against the running app. Security-sensitive admin mutations invalidate the affected user's sessions; storage-space changes refresh smoothly on page load without logout.

**Tech Stack:** Java 8, Spring Boot 2.3, JUnit 5, AssertJ, Mockito/ReflectionTestUtils (from spring-boot-starter-test), vanilla JS frontend.

Spec: `docs/superpowers/specs/2026-07-15-user-and-session-edge-cases-design.md`

---

## File Structure

| File | Responsibility |
|---|---|
| `security/SecureTokenGenerator.java` | add `generateAlphanumeric(int)` |
| `service/FileBoxConfigStore.java` | `generateAdminPassword()` uses alphanumeric generator |
| `service/UserService.java` | last-admin delete guard in `deleteUser`; last-admin role guard in `updateUser` |
| `service/AuthService.java` | `invalidateSessionsForUser(String)`; `refreshStorageSpaces(LoginSession)` |
| `controller/AdminController.java` | self-delete guard + exception surfacing + invalidate triggers in `deleteUser`/`updateUser` |
| `controller/AuthController.java` | `/api/user` calls `refreshStorageSpaces` |
| `static/js/index.js` | no-spaces `Notify.alert` (once per session) |
| `src/test/.../SecureTokenGeneratorTest.java` | new — generator tests |
| `src/test/.../service/UserServiceTest.java` | new — last-admin guard tests |
| `src/test/.../service/AuthServiceTest.java` | new — invalidate + refresh tests |

Base package: `com.wwh.filebox` → `src/main/java/com/wwh/filebox/...`, tests → `src/test/java/com/wwh/filebox/...`.

---

## Task 1: Alphanumeric password generator

**Files:**
- Modify: `src/main/java/com/wwh/filebox/security/SecureTokenGenerator.java`
- Modify: `src/main/java/com/wwh/filebox/service/FileBoxConfigStore.java:170-172`
- Test: `src/test/java/com/wwh/filebox/security/SecureTokenGeneratorTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/wwh/filebox/security/SecureTokenGeneratorTest.java`:

```java
package com.wwh.filebox.security;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    @Test
    void alphanumericHasRequestedLengthAndOnlyAlphanumeric() {
        String pwd = SecureTokenGenerator.generateAlphanumeric(22);
        assertThat(pwd).hasSize(22);
        assertThat(pwd).matches("^[A-Za-z0-9]+$");
    }

    @RepeatedTest(20)
    void alphanumericNeverContainsSymbols() {
        // 反复抽样,确保不出现 Base64-URL 的 - 或 _ / sampled repeatedly, no - or _
        String pwd = SecureTokenGenerator.generateAlphanumeric(22);
        assertThat(pwd).doesNotContain("-", "_", "+", "/", "=");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SecureTokenGeneratorTest`
Expected: compile error — `generateAlphanumeric` does not exist.

- [ ] **Step 3: Implement the generator**

In `SecureTokenGenerator.java`, add a charset field and method (place after `secureRandom` field declaration near the top, and the method after `generateApiKeyToken`):

```java
    // 仅字母(大小写)+数字,方便在控制台复制(无特殊字符) / alphanumeric only, easy console copy, no symbols
    private static final char[] ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /**
     * 生成指定长度的随机字母数字字符串(A–Z a–z 0–9),用于首启/重置的管理员密码。
     * Generate a random alphanumeric string of the given length for admin passwords.
     */
    public static String generateAlphanumeric(int length) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = ALPHANUMERIC[secureRandom.nextInt(ALPHANUMERIC.length)];
        }
        return new String(out);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SecureTokenGeneratorTest`
Expected: PASS (3 tests incl. 20 repetitions of the symbol check).

- [ ] **Step 5: Wire into generateAdminPassword**

In `FileBoxConfigStore.java`, replace the body of `generateAdminPassword` (currently `return SecureTokenGenerator.generateToken().substring(0, 22);`):

```java
    public static String generateAdminPassword() {
        // 仅字母数字,便于首启/重置时从控制台复制 / alphanumeric only, easy to copy from the console
        return SecureTokenGenerator.generateAlphanumeric(22);
    }
```

- [ ] **Step 6: Build to confirm it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/wwh/filebox/security/SecureTokenGenerator.java \
        src/main/java/com/wwh/filebox/service/FileBoxConfigStore.java \
        src/test/java/com/wwh/filebox/security/SecureTokenGeneratorTest.java
git commit -m "feat: 初始/重置管理员密码改为纯字母数字(便于控制台复制)"
```

---

## Task 2: UserService — last-admin delete guard

**Files:**
- Modify: `src/main/java/com/wwh/filebox/service/UserService.java:159-175`
- Test: `src/test/java/com/wwh/filebox/service/UserServiceTest.java` (new)

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/wwh/filebox/service/UserServiceTest.java`:

```java
package com.wwh.filebox.service;

import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.SystemConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    @TempDir
    Path tempDir;
    private Path configPath;
    private ConfigService configService;
    private UserService userService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("filebox.yml");
        // ConfigService.init() 通过该系统属性定位配置文件 / ConfigService.init() resolves via this system property
        System.setProperty(FileBoxConfigStore.CONFIG_PROPERTY, configPath.toString());
        configService = new ConfigService(encoder);
        configService.init();
        userService = new UserService();
        ReflectionTestUtils.setField(userService, "configService", configService);
        ReflectionTestUtils.setField(userService, "passwordEncoder", encoder);
    }

    private void seed(SystemConfig config) {
        configService.saveConfig(config);
    }

    /** 构造含若干 ADMIN 的配置 / build a config with the given admin usernames. */
    private SystemConfig configWithAdmins(String... adminNames) {
        SystemConfig config = FileBoxConfigStore.createDefaultConfig(adminNames[0], encoder.encode("x"));
        List<SystemConfig.UserConfig> users = new ArrayList<>(config.getUsers());
        for (int i = 1; i < adminNames.length; i++) {
            SystemConfig.UserConfig u = new SystemConfig.UserConfig();
            u.setUsername(adminNames[i]);
            u.setPassword(encoder.encode("x"));
            u.setRole(Role.ADMIN.name());
            u.setStorageSpaces(new ArrayList<>());
            users.add(u);
        }
        config.setUsers(users);
        return config;
    }

    @Test
    void deleteUserRefusesToRemoveLastAdmin() {
        seed(configWithAdmins("admin"));
        assertThatThrownBy(() -> userService.deleteUser("admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理员");
    }

    @Test
    void deleteUserAllowsRemovingAdminWhenOthersExist() {
        seed(configWithAdmins("admin", "admin2"));
        assertThat(userService.deleteUser("admin2")).isTrue();
        assertThat(configService.getConfig().getUsers()).extracting(SystemConfig.UserConfig::getUsername)
                .containsExactly("admin");
    }

    @Test
    void deleteUserAllowsRemovingNonAdmin() {
        SystemConfig config = configWithAdmins("admin");
        SystemConfig.UserConfig bob = new SystemConfig.UserConfig();
        bob.setUsername("bob");
        bob.setPassword(encoder.encode("x"));
        bob.setRole(Role.USER.name());
        bob.setStorageSpaces(new ArrayList<>());
        config.getUsers().add(bob);
        seed(config);

        assertThat(userService.deleteUser("bob")).isTrue();
    }
}

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=UserServiceTest`
Expected: `deleteUserRefusesToRemoveLastAdmin` FAILS (no exception thrown — current `deleteUser` just removes the user).

- [ ] **Step 3: Implement the guard**

In `UserService.java`, replace `deleteUser` (lines 159-175) with:

```java
    /**
     * Delete user
     * 删除用户;若目标为最后一个 ADMIN 则拒绝,避免后台被锁死。
     */
    public boolean deleteUser(String username) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return false;
        }

        SystemConfig.UserConfig target = null;
        int adminCount = 0;
        for (SystemConfig.UserConfig u : config.getUsers()) {
            if (Role.ADMIN.name().equals(u.getRole())) {
                adminCount++;
            }
            if (u.getUsername().equals(username)) {
                target = u;
            }
        }

        if (target == null) {
            return false;
        }
        // 最后一个管理员不可删 / cannot remove the last admin
        if (Role.ADMIN.name().equals(target.getRole()) && adminCount <= 1) {
            throw new IllegalStateException("系统至少需要保留一个管理员");
        }

        boolean removed = config.getUsers().removeIf(user -> user.getUsername().equals(username));
        if (removed) {
            configService.saveConfig(config);
            logger.info("User {} deleted", username);
        }
        return removed;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=UserServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wwh/filebox/service/UserService.java \
        src/test/java/com/wwh/filebox/service/UserServiceTest.java
git commit -m "feat: 禁止删除最后一个管理员(UserService 守卫 + 单测)"
```

---

## Task 3: UserService — last-admin role guard

**Files:**
- Modify: `src/main/java/com/wwh/filebox/service/UserService.java:110-157` (`updateUser`)
- Test: `src/test/java/com/wwh/filebox/service/UserServiceTest.java` (append methods)

- [ ] **Step 1: Write the failing tests**

Append to `UserServiceTest.java` (inside the class; reuse the existing `setUp`, `seed`, `configWithAdmins`, and `encoder`):

```java
    private SystemConfig.UserConfig user(String name, Role role) {
        SystemConfig.UserConfig u = new SystemConfig.UserConfig();
        u.setUsername(name);
        u.setPassword(encoder.encode("x"));
        u.setRole(role.name());
        u.setStorageSpaces(new ArrayList<>());
        return u;
    }

    @Test
    void updateUserRefusesToDemoteLastAdmin() {
        seed(configWithAdmins("admin"));
        // 把唯一的 admin 降级为 USER / demote the only admin
        assertThatThrownBy(() ->
                userService.updateUser("admin", null, Role.USER, new String[]{"default"}, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理员");
    }

    @Test
    void updateUserAllowsDemotingAdminWhenOthersExist() {
        seed(configWithAdmins("admin", "admin2"));
        assertThat(userService.updateUser("admin2", null, Role.USER, new String[]{}, "admin2")).isTrue();
        // admin 仍是 ADMIN / admin still ADMIN
        assertThat(configService.getConfig().getUsers()).filteredOn(u -> "admin2".equals(u.getUsername()))
                .singleElement().extracting(SystemConfig.UserConfig::getRole).isEqualTo(Role.USER.name());
    }

    @Test
    void updateUserAllowsChangingNonAdminRole() {
        SystemConfig config = configWithAdmins("admin");
        config.getUsers().add(user("bob", Role.USER));
        seed(config);

        assertThat(userService.updateUser("bob", null, Role.MANAGER, new String[]{}, "bob")).isTrue();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=UserServiceTest`
Expected: `updateUserRefusesToDemoteLastAdmin` FAILS (no exception thrown).

- [ ] **Step 3: Implement the guard**

In `UserService.java`, inside `updateUser`, locate the loop that finds the matching user. Add the last-admin-role check right **after** the rename-collision block and **before** the mutation loop. Concretely, insert this block immediately before the line `for (SystemConfig.UserConfig userConfig : config.getUsers()) {`:

```java
        // 最后一个管理员不可降级 / cannot demote the last admin to a non-admin role
        if (role != Role.ADMIN) {
            int adminCount = 0;
            boolean targetIsAdmin = false;
            for (SystemConfig.UserConfig u : config.getUsers()) {
                if (Role.ADMIN.name().equals(u.getRole())) {
                    adminCount++;
                    if (u.getUsername().equals(username)) {
                        targetIsAdmin = true;
                    }
                }
            }
            if (targetIsAdmin && adminCount <= 1) {
                throw new IllegalStateException("系统至少需要保留一个管理员");
            }
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=UserServiceTest`
Expected: PASS (7 tests total).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wwh/filebox/service/UserService.java \
        src/test/java/com/wwh/filebox/service/UserServiceTest.java
git commit -m "feat: 禁止降级最后一个管理员(updateUser 守卫 + 单测)"
```

---

## Task 4: AuthService — invalidateSessionsForUser

**Files:**
- Modify: `src/main/java/com/wwh/filebox/service/AuthService.java`
- Test: `src/test/java/com/wwh/filebox/service/AuthServiceTest.java` (new)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/wwh/filebox/service/AuthServiceTest.java`:

```java
package com.wwh.filebox.service;

import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.SystemConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    @TempDir
    Path tempDir;
    private ConfigService configService;
    private AuthService authService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        System.setProperty(FileBoxConfigStore.CONFIG_PROPERTY, tempDir.resolve("filebox.yml").toString());
        configService = new ConfigService(encoder);
        configService.init();
        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "configService", configService);
        ReflectionTestUtils.setField(authService, "passwordEncoder", encoder);
    }

    /** 配置一个 USER(拥有 default 空间)/ seed a USER with the default space. */
    private void seedUser(String username, String password) {
        SystemConfig config = FileBoxConfigStore.createDefaultConfig("admin", encoder.encode("adminpw"));
        SystemConfig.UserConfig u = new SystemConfig.UserConfig();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(Role.USER.name());
        u.setStorageSpaces(new ArrayList<>(List.of("default")));
        config.getUsers().add(u);
        configService.saveConfig(config);
    }

    @Test
    void invalidateSessionsForUserKillsAllTheirSessions() {
        seedUser("alice", "pw");
        String t1 = authService.login("alice", "pw");
        String t2 = authService.login("alice", "pw");
        assertThat(t1).isNotNull();
        assertThat(t2).isNotNull();

        authService.invalidateSessionsForUser("alice");

        assertThat(authService.getSession(t1)).isNull();
        assertThat(authService.getSession(t2)).isNull();
    }

    @Test
    void invalidateSessionsForUserLeavesOthersAlone() {
        seedUser("alice", "pw");
        seedUserKeepSpaces("bob", "pw");
        String aliceToken = authService.login("alice", "pw");
        String bobToken = authService.login("bob", "pw");

        authService.invalidateSessionsForUser("alice");

        assertThat(authService.getSession(aliceToken)).isNull();
        assertThat(authService.getSession(bobToken)).isNotNull();
    }

    // seedUser 在 default 空间之外覆盖整张用户表,这里保留 bob 的 default 空间 /
    // seedUser rewrites the whole user table; this keeps an existing second user intact.
    private void seedUserKeepSpaces(String username, String password) {
        SystemConfig config = configService.getConfig();
        SystemConfig.UserConfig u = new SystemConfig.UserConfig();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(Role.USER.name());
        u.setStorageSpaces(new ArrayList<>(List.of("default")));
        config.getUsers().add(u);
        configService.saveConfig(config);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AuthServiceTest`
Expected: compile error — `invalidateSessionsForUser` does not exist.

- [ ] **Step 3: Implement the method**

In `AuthService.java`, add this method (e.g., right after `logout`):

```java
    /**
     * 让某用户的所有会话立即下线(管理员改密码/改角色/删除/改名时调用)。
     * Force all of a user's sessions offline (used on password/role change, deletion, rename).
     */
    public void invalidateSessionsForUser(String username) {
        if (username == null) {
            return;
        }
        sessions.entrySet().removeIf(e -> username.equals(e.getValue().getUsername()));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AuthServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wwh/filebox/service/AuthService.java \
        src/test/java/com/wwh/filebox/service/AuthServiceTest.java
git commit -m "feat: AuthService 新增按用户名下线会话(用于敏感变更强制重登)"
```

---

## Task 5: AuthService — refreshStorageSpaces

**Files:**
- Modify: `src/main/java/com/wwh/filebox/service/AuthService.java`
- Test: `src/test/java/com/wwh/filebox/service/AuthServiceTest.java` (append methods)

- [ ] **Step 1: Write the failing tests**

Append to `AuthServiceTest.java` (inside the class, reusing `setUp`/`configService`/`encoder`):

```java
    private void setSpaces(String username, String... spaces) {
        SystemConfig config = configService.getConfig();
        for (SystemConfig.UserConfig u : config.getUsers()) {
            if (u.getUsername().equals(username)) {
                u.setStorageSpaces(new ArrayList<>(List.of(spaces)));
            }
        }
        configService.saveConfig(config);
    }

    @Test
    void refreshStorageSpacesPicksUpNewlyAssignedSpace() {
        seedUser("alice", "pw");
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"});
        setSpaces("alice", "default", "photos");

        authService.refreshStorageSpaces(session);

        assertThat(session.getStorageSpaces()).containsExactly("default", "photos");
    }

    @Test
    void refreshStorageSpacesClampsRemovedCurrentSpace() {
        seedUser("alice", "pw");
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"});
        assertThat(session.getCurrentStorageSpace()).isEqualTo("default");
        setSpaces("alice", "photos"); // default 被移除 / default removed

        authService.refreshStorageSpaces(session);

        assertThat(session.getStorageSpaces()).containsExactly("photos");
        assertThat(session.getCurrentStorageSpace()).isEqualTo("photos");
    }

    @Test
    void refreshStorageSpacesClearsCurrentWhenEmpty() {
        seedUser("alice", "pw");
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"});
        setSpaces("alice"); // 清空 / cleared

        authService.refreshStorageSpaces(session);

        assertThat(session.getStorageSpaces()).isEmpty();
        assertThat(session.getCurrentStorageSpace()).isNull();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AuthServiceTest`
Expected: compile error — `refreshStorageSpaces` does not exist.

- [ ] **Step 3: Implement the method**

In `AuthService.java`, add this method (e.g., right after `getCurrentUser`):

```java
    /**
     * 按最新配置重派生该会话的可用存储空间,并在当前空间失效时夹紧到首个(或置空)。
     * 使管理员调整空间权限后,用户刷新页面即可生效,无需重新登录。
     * Re-derive this session's storage spaces from the live config and clamp the current
     * space if it was removed — lets admin permission changes apply on page refresh.
     */
    public void refreshStorageSpaces(LoginSession session) {
        if (session == null) {
            return;
        }
        SystemConfig config = configService.getConfig();
        if (config == null) {
            return;
        }
        SystemConfig.UserConfig userConfig = null;
        if (config.getUsers() != null) {
            for (SystemConfig.UserConfig uc : config.getUsers()) {
                if (uc.getUsername().equals(session.getUsername())) {
                    userConfig = uc;
                    break;
                }
            }
        }
        if (userConfig == null) {
            return; // 用户已不存在,保持现状 / user no longer exists; leave as-is
        }

        String[] fresh = getStorageSpacesForUser(userConfig, config);
        session.setStorageSpaces(fresh);

        String current = session.getCurrentStorageSpace();
        boolean currentValid = false;
        if (current != null) {
            for (String s : fresh) {
                if (s.equals(current)) {
                    currentValid = true;
                    break;
                }
            }
        }
        if (!currentValid) {
            session.setCurrentStorageSpace(fresh.length > 0 ? fresh[0] : null);
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AuthServiceTest`
Expected: PASS (5 tests total).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wwh/filebox/service/AuthService.java \
        src/test/java/com/wwh/filebox/service/AuthServiceTest.java
git commit -m "feat: AuthService 按配置重派生会话存储空间(支持刷新生效)"
```

---

## Task 6: AdminController — guards, exception surfacing, invalidate triggers

**Files:**
- Modify: `src/main/java/com/wwh/filebox/controller/AdminController.java:265-305` (`updateUser`, `deleteUser`)

> No new unit test here — the business rules (last-admin guards, invalidate semantics) are already covered in Tasks 2–5. This task wires those services into the controller and is verified manually in Task 9.

- [ ] **Step 1: Add the LoginSession import**

In `AdminController.java`, confirm/add this import (alongside the other `com.wwh.filebox.model.*` imports):

```java
import com.wwh.filebox.model.LoginSession;
```

(`javax.servlet.http.HttpServletRequest` is already imported at the top of the file.)

- [ ] **Step 2: Rewrite deleteUser**

Replace the `deleteUser` method (lines 290-305) with:

```java
    /**
     * Delete user
     * 禁止删除当前登录的自己;被删用户的会话立即失效;最后一个管理员由 service 拒绝。
     */
    @DeleteMapping("/users/{username}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String username, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 禁止删除当前登录的自己 / cannot delete the currently logged-in self
            Object sessionAttr = request.getAttribute("session");
            if (sessionAttr instanceof LoginSession) {
                String caller = ((LoginSession) sessionAttr).getUsername();
                if (username.equals(caller)) {
                    response.put("success", false);
                    response.put("error", "不能删除当前登录的账号");
                    return ResponseEntity.ok(response);
                }
            }

            boolean success = userService.deleteUser(username);
            if (success) {
                // 被删用户的会话立即下线 / kill the deleted user's sessions immediately
                authService.invalidateSessionsForUser(username);
            }
            response.put("success", success);
            if (!success) {
                response.put("error", "删除失败，用户不存在");
            }
        } catch (IllegalStateException e) {
            // 例如:最后一个管理员不可删 / business constraint, e.g. last admin
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
```

- [ ] **Step 3: Rewrite updateUser**

Replace the `updateUser` method (lines 262-288) with:

```java
    /**
     * Update user
     * 改密码/改角色/改名 → 强制该用户下线重新登录;仅改存储空间不打扰。
     * 最后一个管理员降级由 service 拒绝。
     */
    @PutMapping("/users/{username}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String username,
            @RequestBody Map<String, Object> request) {
        String newUsername = (String) request.get("username");
        String password = (String) request.get("password");
        String roleStr = (String) request.get("role");
        @SuppressWarnings("unchecked")
        List<String> storageSpacesList = (List<String>) request.get("storageSpaces");

        Role role = Role.fromString(roleStr);
        String[] storageSpaces = storageSpacesList != null ? storageSpacesList.toArray(new String[0]) : new String[0];

        // 记录变更类型,判断是否需要强制下线 / detect what changed to decide forced re-login
        User existing = userService.getUser(username);
        Role oldRole = existing != null ? existing.getRole() : null;
        boolean passwordChanged = password != null && !password.isEmpty();
        boolean roleChanged = oldRole != null && !oldRole.equals(role);
        String trimmedNew = newUsername == null ? "" : newUsername.trim();
        boolean renamed = !trimmedNew.isEmpty() && !trimmedNew.equals(username);

        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = userService.updateUser(username, password, role, storageSpaces, newUsername);
            response.put("success", success);
            if (success) {
                // 改密码/改角色/改名 → 下线该用户(按旧用户名);仅改空间不打扰
                // password/role change or rename forces re-login (by old username); space-only does not
                if (passwordChanged || roleChanged || renamed) {
                    authService.invalidateSessionsForUser(username);
                }
            } else {
                response.put("error", "更新失败，用户名可能已存在或用户不存在");
            }
        } catch (IllegalStateException e) {
            // 例如:最后一个管理员不可降级 / business constraint, e.g. last admin demotion
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
```

- [ ] **Step 4: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wwh/filebox/controller/AdminController.java
git commit -m "feat: 后台删用户/改用户接入下线与守卫(禁删自己/最后管理员、改密改角下线)"
```

---

## Task 7: AuthController — refresh spaces on /api/user

**Files:**
- Modify: `src/main/java/com/wwh/filebox/controller/AuthController.java:202-228` (`getLegacyUserInfo`)

- [ ] **Step 1: Add the refresh call**

In `AuthController.java`, in `getLegacyUserInfo` (the `@GetMapping("/api/user")` method), find the session null-check and insert the refresh call immediately **after** it. The block currently looks like:

```java
        LoginSession session = authService.getSession(token);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Invalid or expired token\"}");
        }
```

Insert this directly below that block (before `Map<String, Object> userInfo = new HashMap<>();`):

```java
        // 按最新配置刷新会话存储空间,使管理员改空间后刷新即生效(无需重新登录)
        // Refresh this session's spaces from the live config so admin edits apply on reload
        authService.refreshStorageSpaces(session);
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wwh/filebox/controller/AuthController.java
git commit -m "feat: /api/user 按配置刷新会话存储空间(权限变更刷新生效)"
```

---

## Task 8: Frontend — no-storage-space login prompt

**Files:**
- Modify: `src/main/resources/static/js/index.js` (inside the `/api/user` `.then(async data => ...)` handler)

- [ ] **Step 1: Add the prompt**

In `index.js`, inside the `/api/user` `.then(async data => { ... })` block, locate the role display lines:

```js
            const roleNames = { admin: '超级管理员', manager: '管理员', user: '普通用户' };
            document.getElementById('currentRole').textContent = data.isAnonymous
                ? '匿名用户'
                : (roleNames[data.role] || data.role || '—');
```

Insert this block directly **after** those lines:

```js
            // 非匿名、非管理员且无任何存储空间 → 每浏览器会话提示一次
            // non-anonymous, non-admin with no assigned spaces: notify once per session
            if (!window.isAnonymous && data.role !== 'admin' && spaceList.length === 0
                    && !sessionStorage.getItem('filebox_noSpaces_notified')) {
                sessionStorage.setItem('filebox_noSpaces_notified', '1');
                Notify.alert({
                    title: '暂无可用存储空间',
                    content: '管理员尚未为您分配任何存储空间的访问权限，您暂时无法正常使用本系统，请联系管理员处理。',
                    okText: '我知道了'
                });
            }
```

- [ ] **Step 2: Repackage (static resources are served from the jar / a repackage is needed)**

Per project memory, static-resource edits require a repackage. Run:

```bash
mvn -q package -DskipTests
```
Expected: BUILD SUCCESS, `target/file-box-<version>.jar` updated.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/index.js
git commit -m "feat: 无存储空间用户登录后弹出提示(每会话一次)"
```

---

## Task 9: Full build, all tests, manual smoke test

- [ ] **Step 1: Run the entire test suite**

Run: `mvn test`
Expected: BUILD SUCCESS — all existing tests plus the new `SecureTokenGeneratorTest`, `UserServiceTest` (7), `AuthServiceTest` (5) pass.

- [ ] **Step 2: Full package**

Run: `mvn package`
Expected: BUILD SUCCESS producing the jar + release tarball.

- [ ] **Step 3: Start the app and smoke-test each scenario**

Start (dev): `mvn spring-boot:run` (reads `./config/filebox.yml`), or `./dev-restart.sh` if present. Then verify in the browser:

1. **Delete self blocked** — log in as admin → 后台 → 用户管理 → try to delete the row matching your own username → expect toast "不能删除当前登录的账号"; the API path (e.g. via devtools `fetch('/api/admin/users/admin',{method:'DELETE'})`) must also return `{success:false}`.
2. **Last admin delete blocked** — with only one admin, try to delete that admin via the API → expect `{success:false, error:"系统至少需要保留一个管理员"}`. Add a second admin, then deleting the second succeeds.
3. **Last admin demote blocked** — edit the only admin and set role to 普通用户 → expect error "系统至少需要保留一个管理员".
4. **Password reset forces offline** — as admin, reset another user's password → that user's existing session should be invalid (their next request redirects to login / 401). Verify self-service `/api/auth/change-password` does NOT log the user out.
5. **Role change forces offline** — change a USER to MANAGER (or vice versa) → that user is forced to re-login and sees the new role.
6. **Space change applies on refresh** — remove a space from a USER in 后台 → that user refreshes index.html → the space is gone from their switcher immediately, no re-login needed. Add a space back → refresh → it reappears.
7. **No-spaces prompt** — create a USER with zero storage spaces, log in → the "暂无可用存储空间" modal appears once; refresh → does not reappear (sessionStorage flag); a new browser session shows it again.
8. **Alphanumeric password** — delete `config/filebox.yml`, restart → the printed `Initial password:` in `logs/filebox.log` is 22 chars, letters+digits only, no `-`/`_`. (Restore the config afterward.)

- [ ] **Step 4: Final commit if any polish arose**

If the smoke test surfaced small fixes, commit them. Otherwise nothing to commit — the work is complete.

---

## Self-Review (completed during planning)

**Spec coverage:** All six spec items map to tasks — (1) delete guards → Task 2 + Task 6; (2) password charset → Task 1; (3) no-spaces popup → Task 8; (4) space-change refresh → Task 5 + Task 7; (5) session invalidation → Task 4 + Task 6; (6) last-admin role guard → Task 3 + Task 6.

**Placeholder scan:** Each step contains the actual code or exact command; no "TBD"/"add validation" stubs.

**Type/signature consistency:** `invalidateSessionsForUser(String)` and `refreshStorageSpaces(LoginSession)` are defined in Tasks 4/5 and consumed identically in Tasks 6/7. `generateAlphanumeric(int)` defined and wired in Task 1. Error message strings ("系统至少需要保留一个管理员", "不能删除当前登录的账号") match between service, controller, and tests.
