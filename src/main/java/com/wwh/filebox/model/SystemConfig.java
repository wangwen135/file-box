package com.wwh.filebox.model;

import java.util.ArrayList;
import java.util.List;

/**
 * System configuration model
 * 系统配置模型
 */
public class SystemConfig {

    private boolean anonymousUploadEnabled;
    // 匿名访问总开关:能否匿名登录并浏览 / anonymous access gate: can anonymous log in & browse
    private boolean anonymousAccessEnabled;
    private boolean shareNoticeEnabled = true;
    private String allowedOrigins;

    private List<StorageSpaceConfig> storageSpaces = new ArrayList<>();
    private List<UserConfig> users = new ArrayList<>();

    public boolean isAnonymousUploadEnabled() {
        return anonymousUploadEnabled;
    }

    public void setAnonymousUploadEnabled(boolean anonymousUploadEnabled) {
        this.anonymousUploadEnabled = anonymousUploadEnabled;
    }

    public boolean isAnonymousAccessEnabled() {
        return anonymousAccessEnabled;
    }

    public void setAnonymousAccessEnabled(boolean anonymousAccessEnabled) {
        this.anonymousAccessEnabled = anonymousAccessEnabled;
    }

    public boolean isShareNoticeEnabled() {
        return shareNoticeEnabled;
    }

    public void setShareNoticeEnabled(boolean shareNoticeEnabled) {
        this.shareNoticeEnabled = shareNoticeEnabled;
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
        private boolean allowAnonymousAccess;   // 匿名访问(浏览) / anonymous access (browse)
        private boolean allowAnonymousUpload;   // 匿名上传(蕴含访问) / anonymous upload (implies access)

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

        public boolean isAllowAnonymousAccess() {
            return allowAnonymousAccess;
        }

        public void setAllowAnonymousAccess(boolean allowAnonymousAccess) {
            this.allowAnonymousAccess = allowAnonymousAccess;
        }

        public boolean isAllowAnonymousUpload() {
            return allowAnonymousUpload;
        }

        public void setAllowAnonymousUpload(boolean allowAnonymousUpload) {
            this.allowAnonymousUpload = allowAnonymousUpload;
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
