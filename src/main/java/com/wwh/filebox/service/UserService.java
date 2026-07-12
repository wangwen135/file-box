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
        // 必须用可变 List：StorageService.createStorageSpace 会向 admin 的列表 add 新空间，
        // Arrays.asList 返回定长列表，add 会抛 UnsupportedOperationException。
        // Must use a mutable list: createStorageSpace adds new spaces to admin lists;
        // Arrays.asList is fixed-size and throws on add.
        userConfig.setStorageSpaces(new ArrayList<>(java.util.Arrays.asList(storageSpaces)));

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
     * Only updates password if a non-empty password is provided.
     * Renames the user when newUsername is non-empty, differs from the current
     * name, and does not collide with another existing user.
     * 仅当传入非空 password 时更新密码;newUsername 非空、与现名不同且不与他人冲突时才改名。
     */
    public boolean updateUser(String username, String password, Role role, String[] storageSpaces, String newUsername) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getUsers() == null) {
            return false;
        }

        // 是否需要改名 / whether a rename is requested (trim 后判断)
        String trimmedNew = newUsername == null ? "" : newUsername.trim();
        boolean rename = !trimmedNew.isEmpty() && !trimmedNew.equals(username);
        if (rename) {
            // 新用户名不能与已有用户冲突 / new name must not collide with another user
            for (SystemConfig.UserConfig u : config.getUsers()) {
                if (u.getUsername().equals(trimmedNew)) {
                    logger.warn("Rename failed for {}: user {} already exists", username, trimmedNew);
                    return false;
                }
            }
        }

        for (SystemConfig.UserConfig userConfig : config.getUsers()) {
            if (userConfig.getUsername().equals(username)) {
                // Only update password if provided and not empty
                if (password != null && !password.isEmpty()) {
                    userConfig.setPassword(passwordEncoder.encode(password));
                }
                userConfig.setRole(role.name());
                // 同 create：存可变列表，避免后续 add 抛 UnsupportedOperationException。
                // Mutable list, same reason as create.
                userConfig.setStorageSpaces(new ArrayList<>(java.util.Arrays.asList(storageSpaces)));
                if (rename) {
                    userConfig.setUsername(trimmedNew);
                }

                configService.saveConfig(config);
                logger.info("User {} updated{}", username, rename ? " (renamed to " + trimmedNew + ")" : "");
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
     * 校验明文密码是否与指定用户的存储哈希匹配 / Verify a raw password against the stored hash.
     */
    public boolean checkPassword(String username, String rawPassword) {
        User user = getUser(username);
        return user != null && passwordEncoder.matches(rawPassword, user.getPassword());
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
