package com.wwh.filebox.controller;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.SystemConfig;
import com.wwh.filebox.service.AuthService;
import com.wwh.filebox.service.ConfigService;
import com.wwh.filebox.security.LoginAttemptManager;
import com.wwh.filebox.security.SecureTokenGenerator;
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

        // Check if config exists
        if (!configService.configExists()) {
            // First login with default admin credentials
            if (AppConstants.Auth.DEFAULT_ADMIN_USERNAME.equals(username) &&
                AppConstants.Auth.DEFAULT_ADMIN_PASSWORD.equals(password)) {
                // Create default config
                SystemConfig defaultConfig = configService.createDefaultConfig(
                    AppConstants.Auth.DEFAULT_ADMIN_USERNAME,
                    AppConstants.Auth.DEFAULT_ADMIN_PASSWORD_HASH);
                configService.saveConfig(defaultConfig);
                configService.reload(); // Reload config after creating
                logger.info("Default config created for admin user");

                // Log the user in
                String token = authService.login(username, password, true);
                if (token != null) {
                    setTokenCookie(response, token);
                    return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create("/index.html")).build();
                }
            }
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

        // Check if config exists
        if (!configService.configExists()) {
            // First login with default admin credentials
            if (AppConstants.Auth.DEFAULT_ADMIN_USERNAME.equals(username) &&
                AppConstants.Auth.DEFAULT_ADMIN_PASSWORD.equals(password)) {
                // Create default config
                SystemConfig defaultConfig = configService.createDefaultConfig(
                    AppConstants.Auth.DEFAULT_ADMIN_USERNAME,
                    AppConstants.Auth.DEFAULT_ADMIN_PASSWORD_HASH);
                configService.saveConfig(defaultConfig);
                logger.info("Default config created for admin user");

                // Log the user in
                String token = authService.login(username, password, rememberMe);
                if (token != null) {
                    setTokenCookie(response, token);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("token", token);
                    return ResponseEntity.ok(result);
                }
            }
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
