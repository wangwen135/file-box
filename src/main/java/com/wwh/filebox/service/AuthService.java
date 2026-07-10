package com.wwh.filebox.service;

import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.SystemConfig;
import com.wwh.filebox.model.User;
import com.wwh.filebox.security.SecureTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication service
 * 认证服务
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // Session storage: token -> LoginSession
    private final Map<String, LoginSession> sessions = new ConcurrentHashMap<>();

    /**
     * Login with username and password
     */
    public String login(String username, String password) {
        return login(username, password, true);
    }

    /**
     * Login with username, password and remember me option
     */
    public String login(String username, String password, boolean rememberMe) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            logger.error("Configuration not loaded");
            return null;
        }

        logger.debug("Login attempt for user: {}", username);

        for (SystemConfig.UserConfig userConfig : config.getUsers()) {
            if (userConfig.getUsername().equals(username)) {
                // 验证密码（移除硬编码fallback，使用标准的BCrypt验证）
                boolean matches = passwordEncoder.matches(password, userConfig.getPassword());
                logger.debug("Password verification for user {}: {}", username, matches ? "success" : "failed");

                if (matches) {
                    // Create session with remember me setting
                    String token = generateToken();
                    String[] storageSpaces = getStorageSpacesForUser(userConfig, config);

                    LoginSession session = new LoginSession(username, Role.fromString(userConfig.getRole()), storageSpaces, rememberMe);
                    sessions.put(token, session);

                    logger.info("User {} logged in successfully", username);
                    return token;
                }
            }
        }

        logger.warn("Login failed for user: {}", username);
        return null;
    }

    public String loginAnonymous() {
        SystemConfig config = configService.getConfig();
        if (config == null || !config.isAnonymousUploadEnabled() || config.getStorageSpaces() == null) {
            return null;
        }

        List<String> anonymousSpaces = new ArrayList<>();
        for (SystemConfig.StorageSpaceConfig space : config.getStorageSpaces()) {
            if (space.isAllowAnonymous()) {
                anonymousSpaces.add(space.getName());
            }
        }
        if (anonymousSpaces.isEmpty()) {
            return null;
        }

        String token = generateToken();
        LoginSession session = new LoginSession("anonymous", Role.USER, anonymousSpaces.toArray(new String[0]), false);
        sessions.put(token, session);
        logger.info("Anonymous session created with {} storage space(s)", anonymousSpaces.size());
        return token;
    }

    /**
     * 获取用户的存储空间列表
     * ADMIN用户自动获取所有存储空间，普通用户获取分配的存储空间
     *
     * @param userConfig 用户配置
     * @param config 系统配置
     * @return 存储空间名称数组
     */
    private String[] getStorageSpacesForUser(SystemConfig.UserConfig userConfig, SystemConfig config) {
        // Admin users get all available storage spaces
        if ("ADMIN".equals(userConfig.getRole())) {
            List<String> allSpaces = new ArrayList<>();
            if (config.getStorageSpaces() != null) {
                for (SystemConfig.StorageSpaceConfig space : config.getStorageSpaces()) {
                    allSpaces.add(space.getName());
                }
            }
            return allSpaces.toArray(new String[0]);
        } else {
            // Regular users get their assigned storage spaces
            return userConfig.getStorageSpaces().toArray(new String[0]);
        }
    }

    /**
     * Logout
     */
    public void logout(String token) {
        LoginSession session = sessions.remove(token);
        if (session != null) {
            logger.info("User {} logged out", session.getUsername());
        }
    }

    /**
     * Get session by token
     */
    public LoginSession getSession(String token) {
        if (token == null) {
            return null;
        }
        LoginSession session = sessions.get(token);
        if (session != null) {
            if (session.isExpired()) {
                sessions.remove(token);
                return null;
            }
            // Extend session expiry on access
            session.extendExpiry();
        }
        return session;
    }

    /**
     * Get current user by token
     */
    public User getCurrentUser(String token) {
        LoginSession session = getSession(token);
        if (session == null) {
            return null;
        }

        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return null;
        }

        for (SystemConfig.UserConfig userConfig : config.getUsers()) {
            if (userConfig.getUsername().equals(session.getUsername())) {
                User user = new User();
                user.setUsername(userConfig.getUsername());
                user.setPassword(userConfig.getPassword());
                user.setRole(Role.fromString(userConfig.getRole()));
                user.setStorageSpaces(getStorageSpacesForUser(userConfig, config));
                user.setCurrentStorageSpace(session.getCurrentStorageSpace());
                return user;
            }
        }

        return null;
    }

    /**
     * Check if user is admin
     */
    public boolean isAdmin(String token) {
        LoginSession session = getSession(token);
        return session != null && Role.ADMIN.equals(session.getRole());
    }

    /**
     * Check if user is manager or admin
     */
    public boolean isManagerOrAdmin(String token) {
        LoginSession session = getSession(token);
        if (session == null) {
            return false;
        }
        Role role = session.getRole();
        return Role.ADMIN.equals(role) || Role.MANAGER.equals(role);
    }

    /**
     * Update current storage space for user
     */
    public boolean switchStorageSpace(String token, String storageSpace) {
        LoginSession session = getSession(token);
        if (session == null) {
            return false;
        }

        // Check if user has access to this storage space
        for (String space : session.getStorageSpaces()) {
            if (space.equals(storageSpace)) {
                session.setCurrentStorageSpace(storageSpace);
                return true;
            }
        }

        return false;
    }

    /**
     * Generate cryptographically secure random token
     */
    private String generateToken() {
        return SecureTokenGenerator.generateToken();
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Get all active sessions
     */
    public List<Map<String, Object>> getActiveSessions() {
        List<Map<String, Object>> sessionList = new ArrayList<>();
        for (Map.Entry<String, LoginSession> entry : sessions.entrySet()) {
            Map<String, Object> sessionInfo = new java.util.HashMap<>();
            LoginSession session = entry.getValue();
            sessionInfo.put("token", entry.getKey());
            sessionInfo.put("username", session.getUsername());
            sessionInfo.put("role", session.getRole().name());
            sessionInfo.put("storageSpace", session.getCurrentStorageSpace());
            sessionInfo.put("loginTime", session.getLoginTime());
            sessionInfo.put("expiryTime", session.getExpiryTime());
            sessionList.add(sessionInfo);
        }
        return sessionList;
    }
}
