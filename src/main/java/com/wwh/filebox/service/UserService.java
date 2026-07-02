package com.wwh.filebox.service;

import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.SystemConfig;
import com.wwh.filebox.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User service
 * 用户服务
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return new ArrayList<>();
        }

        return config.getUsers().stream()
                .map(userConfig -> {
                    User user = new User();
                    user.setUsername(userConfig.getUsername());
                    user.setPassword(userConfig.getPassword());
                    user.setRole(Role.fromString(userConfig.getRole()));
                    user.setStorageSpaces(userConfig.getStorageSpaces().toArray(new String[0]));
                    return user;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get user by username
     */
    public User getUser(String username) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return null;
        }

        for (SystemConfig.UserConfig userConfig : config.getUsers()) {
            if (userConfig.getUsername().equals(username)) {
                User user = new User();
                user.setUsername(userConfig.getUsername());
                user.setPassword(userConfig.getPassword());
                user.setRole(Role.fromString(userConfig.getRole()));
                user.setStorageSpaces(userConfig.getStorageSpaces().toArray(new String[0]));
                return user;
            }
        }

        return null;
    }

    /**
     * Create user
     */
    public boolean createUser(String username, String password, Role role, String[] storageSpaces) {
        SystemConfig config = configService.getConfig();
        if (config == null) {
            return false;
        }

        // Check if user already exists
        if (getUser(username) != null) {
            logger.warn("User {} already exists", username);
            return false;
        }

        SystemConfig.UserConfig userConfig = new SystemConfig.UserConfig();
        userConfig.setUsername(username);
        userConfig.setPassword(passwordEncoder.encode(password));
        userConfig.setRole(role.name());
        userConfig.setStorageSpaces(java.util.Arrays.asList(storageSpaces));

        if (config.getUsers() == null) {
            config.setUsers(new ArrayList<>());
        }
        config.getUsers().add(userConfig);

        configService.saveConfig(config);
        logger.info("User {} created", username);
        return true;
    }

    /**
     * Update user
     * Only updates password if a non-empty password is provided
     */
    public boolean updateUser(String username, String password, Role role, String[] storageSpaces) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return false;
        }

        for (SystemConfig.UserConfig userConfig : config.getUsers()) {
            if (userConfig.getUsername().equals(username)) {
                // Only update password if provided and not empty
                if (password != null && !password.isEmpty()) {
                    userConfig.setPassword(passwordEncoder.encode(password));
                }
                userConfig.setRole(role.name());
                userConfig.setStorageSpaces(java.util.Arrays.asList(storageSpaces));

                configService.saveConfig(config);
                logger.info("User {} updated", username);
                return true;
            }
        }

        return false;
    }

    /**
     * Delete user
     */
    public boolean deleteUser(String username) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return false;
        }

        boolean removed = config.getUsers().removeIf(user -> user.getUsername().equals(username));
        if (removed) {
            configService.saveConfig(config);
            logger.info("User {} deleted", username);
        }

        return removed;
    }

    /**
     * Change password
     */
    public boolean changePassword(String username, String newPassword) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return false;
        }

        for (SystemConfig.UserConfig userConfig : config.getUsers()) {
            if (userConfig.getUsername().equals(username)) {
                userConfig.setPassword(passwordEncoder.encode(newPassword));
                configService.saveConfig(config);
                logger.info("Password changed for user {}", username);
                return true;
            }
        }

        return false;
    }
}
