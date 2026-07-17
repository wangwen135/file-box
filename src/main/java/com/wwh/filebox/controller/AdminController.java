package com.wwh.filebox.controller;

import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.StorageSpace;
import com.wwh.filebox.model.StorageStats;
import com.wwh.filebox.model.SystemConfig;
import com.wwh.filebox.model.User;
import com.wwh.filebox.service.AuthService;
import com.wwh.filebox.service.ConfigService;
import com.wwh.filebox.service.StorageService;
import com.wwh.filebox.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin controller
 * 管理员控制器
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private StorageService storageService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Generate password hash (for testing/admin use)
     * 只返回哈希值，不返回明文密码，避免敏感信息泄露
     *
     * @param password 要哈希的密码
     * @return 包含哈希值的响应
     */
    @GetMapping("/gen-password")
    public ResponseEntity<Map<String, String>> generatePassword(@RequestParam String password) {
        String hash = passwordEncoder.encode(password);
        Map<String, String> result = new HashMap<>();
        // 只返回哈希值，不返回明文密码
        result.put("hash", hash);
        return ResponseEntity.ok(result);
    }

    /**
     * Dashboard statistics
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        // Storage stats
        List<StorageStats> storageStats = storageService.getAllStorageStats();
        dashboard.put("storageStats", storageStats);

        // Total files count
        int totalFiles = storageStats.stream().mapToInt(StorageStats::getFileCount).sum();
        dashboard.put("totalFiles", totalFiles);

        // Total used space
        long totalUsed = storageStats.stream().mapToLong(StorageStats::getUsedSize).sum();
        dashboard.put("totalUsed", totalUsed);

        // Total space
        long totalSpace = storageStats.stream().mapToLong(StorageStats::getTotalSize).sum();
        dashboard.put("totalSpace", totalSpace);

        // Active sessions
        int activeSessions = authService.getActiveSessionCount();
        dashboard.put("activeSessions", activeSessions);
        dashboard.put("uptime", formatUptime(ManagementFactory.getRuntimeMXBean().getUptime()));

        return ResponseEntity.ok(dashboard);
    }

    private String formatUptime(long uptimeMillis) {
        long totalSeconds = uptimeMillis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (days > 0) {
            return days + " 天 " + hours + " 小时";
        }
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分钟";
        }
        return Math.max(minutes, 1) + " 分钟";
    }

    /**
     * Get storage spaces (alias: /storage)
     */
    @GetMapping({"/storages", "/storage"})
    public ResponseEntity<List<Map<String, Object>>> getStorages() {
        List<StorageSpace> spaces = storageService.getAllStorageSpaces();
        List<Map<String, Object>> result = new ArrayList<>();

        for (StorageSpace space : spaces) {
            Map<String, Object> spaceMap = new HashMap<>();
            spaceMap.put("name", space.getName());
            spaceMap.put("path", space.getPath());
            spaceMap.put("maxSize", space.getFormattedMaxSize());
            spaceMap.put("maxSizeBytes", space.getMaxSizeInBytes());
            spaceMap.put("allowAnonymousAccess", space.isAllowAnonymousAccess());
            spaceMap.put("allowAnonymousUpload", space.isAllowAnonymousUpload());

            StorageStats stats = storageService.getStorageStats(space.getName());
            if (stats != null) {
                spaceMap.put("usedSize", stats.getFormattedUsedSize());
                spaceMap.put("freeSize", stats.getFormattedFreeSize());
                spaceMap.put("fileCount", stats.getFileCount());
                spaceMap.put("directoryCount", stats.getDirectoryCount());
                spaceMap.put("usagePercentage", stats.getUsagePercentage());
            }

            result.add(spaceMap);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Create storage space
     */
    @PostMapping({"/storages", "/storage"})
    public ResponseEntity<Map<String, Object>> createStorage(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String path = (String) request.get("path");
        String maxSize = (String) request.get("maxSize");
        Boolean allowAnonymousAccess = request.get("allowAnonymousAccess") != null ? (Boolean) request.get("allowAnonymousAccess") : false;
        Boolean allowAnonymousUpload = request.get("allowAnonymousUpload") != null ? (Boolean) request.get("allowAnonymousUpload") : false;

        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = storageService.createStorageSpace(name, path, maxSize, allowAnonymousAccess, allowAnonymousUpload);
            response.put("success", success);
            if (!success) {
                response.put("error", "创建存储空间失败：名称不合规或已存在");
            }
        } catch (IllegalStateException e) {
            // 目录创建失败等带具体原因的异常 / failure carrying a specific reason (e.g. dir creation)
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Update storage space
     */
    @PutMapping({"/storages/{name}", "/storage/{name}"})
    public ResponseEntity<Map<String, Object>> updateStorage(
            @PathVariable String name,
            @RequestBody Map<String, Object> request) {
        String newName = (String) request.get("name"); // 期望的新名称（缺省/与原名相同则不改名）
        String path = (String) request.get("path");
        String maxSize = (String) request.get("maxSize");
        Boolean allowAnonymousAccess = request.get("allowAnonymousAccess") != null ? (Boolean) request.get("allowAnonymousAccess") : false;
        Boolean allowAnonymousUpload = request.get("allowAnonymousUpload") != null ? (Boolean) request.get("allowAnonymousUpload") : false;

        // 是否真正改名（与 StorageService 共用同一套判定）/ is this actually a rename?
        boolean renameRequested = StorageService.shouldRename(name, newName);

        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = storageService.updateStorageSpace(name, newName, path, maxSize, allowAnonymousAccess, allowAnonymousUpload);
            response.put("success", success);
            if (!success) {
                response.put("error", "更新存储空间失败：名称不合规、已存在，或原空间/路径无效");
            } else if (renameRequested) {
                // 持久化成功后再迁移活跃会话，避免指向未落盘的名字
                authService.renameInSessions(name, newName.trim());
            }
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Delete storage space
     */
    @DeleteMapping({"/storages/{name}", "/storage/{name}"})
    public ResponseEntity<Map<String, Object>> deleteStorage(@PathVariable String name) {
        boolean success = storageService.deleteStorageSpace(name);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (!success) {
            response.put("error", "Failed to delete storage space");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get users
     */
    @GetMapping({"/users", "/user"})
    public ResponseEntity<List<User>> getUsers() {
        List<User> users = userService.getAllUsers();

        // Remove passwords from response
        List<User> safeUsers = users.stream()
                .map(user -> {
                    User safeUser = new User();
                    safeUser.setUsername(user.getUsername());
                    safeUser.setRole(user.getRole());
                    safeUser.setStorageSpaces(user.getStorageSpaces());
                    return safeUser;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(safeUsers);
    }

    /**
     * Create user
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> request) {
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String roleStr = (String) request.get("role");
        @SuppressWarnings("unchecked")
        List<String> storageSpacesList = (List<String>) request.get("storageSpaces");

        Role role = Role.fromString(roleStr);
        String[] storageSpaces = storageSpacesList != null ? storageSpacesList.toArray(new String[0]) : new String[0];

        boolean success = userService.createUser(username, password, role, storageSpaces);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (!success) {
            response.put("error", "Failed to create user");
        }

        return ResponseEntity.ok(response);
    }

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

    /**
     * Get system config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        SystemConfig config = configService.getConfig();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("config", config);

        return ResponseEntity.ok(response);
    }

    /**
     * Update system config
     */
    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> request) {
        SystemConfig config = configService.getConfig();
        if (config == null) {
            config = new SystemConfig();
        }

        if (request.containsKey("shareNoticeEnabled")) {
            config.setShareNoticeEnabled(Boolean.parseBoolean(String.valueOf(request.get("shareNoticeEnabled"))));
        }

        configService.saveConfig(config);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        return ResponseEntity.ok(response);
    }

    /**
     * Get active sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getSessions() {
        List<Map<String, Object>> sessions = authService.getActiveSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get single storage space
     */
    @GetMapping("/storage/{name}")
    public ResponseEntity<Map<String, Object>> getStorage(@PathVariable String name) {
        StorageSpace space = storageService.getStorageSpace(name);
        if (space == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("name", space.getName());
        response.put("path", space.getPath());
        response.put("maxSize", space.getFormattedMaxSize());
        response.put("allowAnonymousAccess", space.isAllowAnonymousAccess());
        response.put("allowAnonymousUpload", space.isAllowAnonymousUpload());

        return ResponseEntity.ok(response);
    }

    /**
     * Get storage stats
     */
    @GetMapping("/storage/{name}/stats")
    public ResponseEntity<StorageStats> getStorageStats(@PathVariable String name) {
        StorageStats stats = storageService.getStorageStats(name);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * Get single user
     */
    @GetMapping("/users/{username}")
    public ResponseEntity<User> getUser(@PathVariable String username) {
        User user = userService.getUser(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        // Remove password from response
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    /**
     * Update anonymous config
     */
    @PutMapping("/anonymous")
    public ResponseEntity<Map<String, Object>> updateAnonymous(@RequestBody Map<String, Object> request) {
        SystemConfig config = configService.getConfig();
        if (config == null) {
            config = new SystemConfig();
        }

        // 两个新字段:访问总开关 + 上传开关;兼容旧版单字段 enabled(视作同时设置两者)
        // Two new fields (access gate + upload); legacy single "enabled" sets both.
        Boolean accessEnabled = (Boolean) request.get("anonymousAccessEnabled");
        Boolean uploadEnabled = (Boolean) request.get("anonymousUploadEnabled");
        if (accessEnabled == null && uploadEnabled == null) {
            Boolean legacy = (Boolean) request.get("enabled");
            if (legacy == null) {
                legacy = (Boolean) request.get("anonymous-upload-enabled");
            }
            if (legacy != null) {
                accessEnabled = legacy;
                uploadEnabled = legacy;
            }
        }
        if (accessEnabled != null) {
            config.setAnonymousAccessEnabled(accessEnabled);
        }
        if (uploadEnabled != null) {
            config.setAnonymousUploadEnabled(uploadEnabled);
        }
        // 强制 upload ⇒ access / enforce upload implies access
        if (config.isAnonymousUploadEnabled() && !config.isAnonymousAccessEnabled()) {
            config.setAnonymousAccessEnabled(true);
        }
        if (!config.isAnonymousAccessEnabled()) {
            config.setAnonymousUploadEnabled(false);
        }

        configService.saveConfig(config);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("anonymousAccessEnabled", config.isAnonymousAccessEnabled());
        response.put("anonymousUploadEnabled", config.isAnonymousUploadEnabled());

        return ResponseEntity.ok(response);
    }
}
