package com.wwh.filebox.controller;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.SystemConfig;
import com.wwh.filebox.service.AuthService;
import com.wwh.filebox.service.ConfigService;
import com.wwh.filebox.service.UserService;
import com.wwh.filebox.security.LoginAttemptManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller
 * 认证控制器
 */
@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private LoginAttemptManager loginAttemptService;

    @Autowired
    private UserService userService;

    /**
     * Default login endpoint (legacy support)
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginPost(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletResponse response) {

        logger.info("User login attempt: {}", username);

        // Check if user is locked
        if (loginAttemptService.isLocked(username)) {
            long remainingLockTime = loginAttemptService.getRemainingLockTime(username);
            logger.warn("User {} is locked, remaining lock time: {} seconds", username, remainingLockTime);
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body("登录失败次数过多，请" + remainingLockTime + "秒后重试");
        }

        // Try to login
        String token = authService.login(username, password, true);

        if (token == null) {
            // Login failed, record attempt
            if (loginAttemptService.loginFailed(username)) {
                long remainingLockTime = loginAttemptService.getRemainingLockTime(username);
                return ResponseEntity.status(HttpStatus.LOCKED)
                        .body("登录失败次数过多，请" + remainingLockTime + "秒后重试");
            }

            logger.warn("User login failed: {}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
        }

        // Login successful, reset failed attempts
        loginAttemptService.loginSucceeded(username);

        // Set token cookie
        setTokenCookie(response, token);

        logger.info("User {} logged in successfully", username);
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create("/index.html")).build();
    }

    /**
     * New API login endpoint
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> apiLogin(@RequestBody Map<String, String> request, HttpServletResponse response) {
        String username = request.get("username");
        String password = request.get("password");
        Boolean rememberMe = request.get("rememberMe") != null ? Boolean.valueOf(request.get("rememberMe")) : true;

        logger.info("API login attempt: {}", username);

        // Check if user is locked
        if (loginAttemptService.isLocked(username)) {
            long remainingLockTime = loginAttemptService.getRemainingLockTime(username);
            logger.warn("User {} is locked, remaining lock time: {} seconds", username, remainingLockTime);
            Map<String, Object> lockedResult = new HashMap<>();
            lockedResult.put("success", false);
            lockedResult.put("error", "登录失败次数过多，请" + remainingLockTime + "秒后重试");
            return ResponseEntity.status(HttpStatus.LOCKED).body(lockedResult);
        }

        // Try to login
        String token = authService.login(username, password, rememberMe);

        if (token == null) {
            // Login failed, record attempt
            if (loginAttemptService.loginFailed(username)) {
                long remainingLockTime = loginAttemptService.getRemainingLockTime(username);
                Map<String, Object> lockedResult = new HashMap<>();
                lockedResult.put("success", false);
                lockedResult.put("error", "登录失败次数过多，请" + remainingLockTime + "秒后重试");
                return ResponseEntity.status(HttpStatus.LOCKED).body(lockedResult);
            }

            logger.warn("API login failed: {}", username);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "用户名或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        // Login successful, reset failed attempts
        loginAttemptService.loginSucceeded(username);

        // Set token cookie
        setTokenCookie(response, token);

        logger.info("User {} logged in successfully via API", username);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("token", token);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "token", required = false) String token) {
        if (token != null) {
            authService.logout(token);
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create("/login.html")).build();
    }

    /**
     * Get current user info
     */
    @GetMapping("/api/auth/user")
    @ResponseBody
    public ResponseEntity<?> getUserInfo(@CookieValue(value = "token", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Not logged in\"}");
        }

        LoginSession session = authService.getSession(token);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Invalid or expired token\"}");
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", session.getUsername());
        userInfo.put("role", session.getRole().name());
        userInfo.put("storageSpaces", session.getStorageSpaces());
        userInfo.put("currentStorageSpace", session.getCurrentStorageSpace());

        return ResponseEntity.ok(userInfo);
    }

    /**
     * 旧前端兼容接口:当前用户信息 / Legacy current-user endpoint for the old frontend.
     *
     * index.html 仍调用 /api/user(2026-03 重构前的旧路径)。重构把端点改成了
     * /api/auth/user 但前端未同步,导致登录后 index.html 的 fetch('/api/user') 命中 404
     * → /error → 重定向回 /index.html → 返回 HTML → response.json() 抛异常 → .catch
     * 又跳回 /login.html,表现为“登录成功后立即弹回登录页”。此处保留旧端点、返回旧字段
     * 结构 {groupName, username, role, isAnonymous} 以恢复前端契约。
     */
    @GetMapping("/api/user")
    @ResponseBody
    public ResponseEntity<?> getLegacyUserInfo(@CookieValue(value = "token", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Not logged in\"}");
        }

        LoginSession session = authService.getSession(token);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Invalid or expired token\"}");
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", session.getUsername());
        // 前端 index.html 以小写比较 role === 'admin',这里返回小写以保持兼容
        userInfo.put("role", session.getRole().name().toLowerCase());
        // groupName 旧指“用户组”(已随 GroupConfig 移除),这里用当前存储空间名代替,保留头部上下文显示
        userInfo.put("groupName", session.getCurrentStorageSpace());
        // 顶栏存储空间切换器需要可选列表与当前空间 / for the header storage-space switcher
        userInfo.put("storageSpaces", session.getStorageSpaces());
        userInfo.put("currentStorageSpace", session.getCurrentStorageSpace());
        // 经 token 鉴权的均为已登录用户,非匿名
        userInfo.put("isAnonymous", false);
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 修改当前登录用户密码 / Change the current user's password.
     * 需校验当前密码,新密码需满足最小长度且与当前密码不同。
     */
    @PostMapping("/api/auth/change-password")
    @ResponseBody
    public ResponseEntity<?> changePassword(
            @CookieValue(value = "token", required = false) String token,
            @RequestBody Map<String, String> request) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"未登录\"}");
        }

        LoginSession session = authService.getSession(token);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"登录已过期,请重新登录\"}");
        }

        String username = session.getUsername();
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        // 基础非空校验 / basic presence checks
        if (currentPassword == null || currentPassword.isEmpty() ||
            newPassword == null || newPassword.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "请填写当前密码和新密码");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        // 校验当前密码 / verify the current password
        if (!userService.checkPassword(username, currentPassword)) {
            logger.warn("Change password failed (wrong current password): {}", username);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "当前密码错误");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        // 新密码长度 / min length
        if (newPassword.length() < AppConstants.Auth.MIN_PASSWORD_LENGTH) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "新密码至少 " + AppConstants.Auth.MIN_PASSWORD_LENGTH + " 位");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        // 新密码不得与当前密码相同 / new password must differ
        if (newPassword.equals(currentPassword)) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "新密码不能与当前密码相同");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        boolean ok = userService.changePassword(username, newPassword);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        if (!ok) {
            result.put("error", "修改失败,请重试");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        logger.info("User {} changed password successfully", username);
        return ResponseEntity.ok(result);
    }

    /**
     * Switch storage space
     */
    @PostMapping("/api/auth/switch-storage")
    @ResponseBody
    public ResponseEntity<?> switchStorageSpace(
            @CookieValue(value = "token", required = false) String token,
            @RequestBody Map<String, String> request) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Not logged in\"}");
        }

        String storageSpace = request.get("storageSpace");
        boolean success = authService.switchStorageSpace(token, storageSpace);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        if (!success) {
            result.put("error", "Failed to switch storage space");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Get anonymous access config
     */
    @GetMapping("/api/auth/anonymous-config")
    @ResponseBody
    public ResponseEntity<?> getAnonymousConfig() {
        SystemConfig config = configService.getConfig();
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("enabled", config != null && config.isAnonymousUploadEnabled());
        configInfo.put("configExists", configService.configExists());
        return ResponseEntity.ok(configInfo);
    }

    /**
     * Get system config
     */
    @GetMapping("/api/auth/config")
    @ResponseBody
    public ResponseEntity<?> getConfig() {
        SystemConfig config = configService.getConfig();
        if (config == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("configExists", false);
            return ResponseEntity.ok(result);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("configExists", true);
        result.put("name", config.getName());
        result.put("description", config.getDescription());
        return ResponseEntity.ok(result);
    }

    /**
     * 设置Token Cookie
     * 添加安全属性：HttpOnly、SameSite
     *
     * @param response HTTP响应
     * @param token 认证令牌
     */
    private void setTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(AppConstants.Auth.TOKEN_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(AppConstants.Auth.TOKEN_COOKIE_MAX_AGE);
        // 添加SameSite属性防止CSRF攻击（需要Servlet 3.1+或通过响应头设置）
        // 在响应头中设置SameSite属性
        response.setHeader("Set-Cookie",
            String.format("%s=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                AppConstants.Auth.TOKEN_COOKIE_NAME,
                token,
                AppConstants.Auth.TOKEN_COOKIE_MAX_AGE));
    }
}
