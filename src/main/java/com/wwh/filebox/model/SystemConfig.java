package com.wwh.filebox.model;

import java.util.ArrayList;
import java.util.List;

/**
 * System configuration model
 * 系统配置模型
 */
public class SystemConfig {

    private String name;
    private String description;
    private boolean anonymousUploadEnabled;
    private String allowedOrigins;

    private List<StorageSpaceConfig> storageSpaces = new ArrayList<>();
    private List<UserConfig> users = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAnonymousUploadEnabled() {
        return anonymousUploadEnabled;
    }

    public void setAnonymousUploadEnabled(boolean anonymousUploadEnabled) {
        this.anonymousUploadEnabled = anonymousUploadEnabled;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<StorageSpaceConfig> getStorageSpaces() {
        return storageSpaces;
    }

    public void setStorageSpaces(List<StorageSpaceConfig> storageSpaces) {
        this.storageSpaces = storageSpaces;
    }

    public List<UserConfig> getUsers() {
        return users;
    }

    public void setUsers(List<UserConfig> users) {
        this.users = users;
    }

    public static class StorageSpaceConfig {
        private String name;
        private String path;
        private String maxSize;
        private String urlPrefix;
        private boolean allowAnonymous;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(String maxSize) {
            this.maxSize = maxSize;
        }

        public String getUrlPrefix() {
            return urlPrefix;
        }

        public void setUrlPrefix(String urlPrefix) {
            this.urlPrefix = urlPrefix;
        }

        public boolean isAllowAnonymous() {
            return allowAnonymous;
        }

        public void setAllowAnonymous(boolean allowAnonymous) {
            this.allowAnonymous = allowAnonymous;
        }
    }

    public static class UserConfig {
        private String username;
        private String password;
        private String role;
        private List<String> storageSpaces = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<String> getStorageSpaces() {
            return storageSpaces;
        }

        public void setStorageSpaces(List<String> storageSpaces) {
            this.storageSpaces = storageSpaces;
        }
    }
}
