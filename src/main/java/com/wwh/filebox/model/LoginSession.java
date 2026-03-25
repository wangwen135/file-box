package com.wwh.filebox.model;

/**
 * Login session model
 * 登录会话模型
 */
public class LoginSession {

    private String username;
    private Role role;
    private String[] storageSpaces;
    private String currentStorageSpace;
    private long loginTime;
    private long expiryTime;
    private boolean rememberMe;

    public LoginSession() {
    }

    public LoginSession(String username, Role role, String[] storageSpaces) {
        this(username, role, storageSpaces, true);
    }

    public LoginSession(String username, Role role, String[] storageSpaces, boolean rememberMe) {
        this.username = username;
        this.role = role;
        this.storageSpaces = storageSpaces;
        this.currentStorageSpace = storageSpaces != null && storageSpaces.length > 0 ? storageSpaces[0] : null;
        this.loginTime = System.currentTimeMillis();
        this.rememberMe = rememberMe;
        long expiryDuration = rememberMe ? 30L * 24 * 60 * 60 * 1000 : 24 * 60 * 60 * 1000; // 30 days or 1 day
        this.expiryTime = System.currentTimeMillis() + expiryDuration;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    public void extendExpiry() {
        long expiryDuration = rememberMe ? 30L * 24 * 60 * 60 * 1000 : 24 * 60 * 60 * 1000;
        this.expiryTime = System.currentTimeMillis() + expiryDuration;
    }

    public void extendExpiry(boolean rememberMe) {
        this.rememberMe = rememberMe;
        long expiryDuration = rememberMe ? 30L * 24 * 60 * 60 * 1000 : 24 * 60 * 60 * 1000;
        this.expiryTime = System.currentTimeMillis() + expiryDuration;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String[] getStorageSpaces() {
        return storageSpaces;
    }

    public void setStorageSpaces(String[] storageSpaces) {
        this.storageSpaces = storageSpaces;
    }

    public String getCurrentStorageSpace() {
        return currentStorageSpace;
    }

    public void setCurrentStorageSpace(String currentStorageSpace) {
        this.currentStorageSpace = currentStorageSpace;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(long loginTime) {
        this.loginTime = loginTime;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
