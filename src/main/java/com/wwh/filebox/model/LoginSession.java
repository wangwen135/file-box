package com.wwh.filebox.model;

import com.wwh.filebox.constants.AppConstants;

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
    private String loginIp;        // 登录 IP / login IP
    private boolean anonymous;     // 是否匿名会话 / whether this is an anonymous session
    private long lastActiveMillis; // 最近一次活跃时间 / last active time

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
        long expiryDuration = rememberMe ? AppConstants.Auth.SESSION_TTL_REMEMBER_ME_MS : AppConstants.Auth.SESSION_TTL_DEFAULT_MS;
        this.expiryTime = System.currentTimeMillis() + expiryDuration;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    public void extendExpiry() {
        long expiryDuration = rememberMe ? AppConstants.Auth.SESSION_TTL_REMEMBER_ME_MS : AppConstants.Auth.SESSION_TTL_DEFAULT_MS;
        this.expiryTime = System.currentTimeMillis() + expiryDuration;
    }

    public void extendExpiry(boolean rememberMe) {
        this.rememberMe = rememberMe;
        long expiryDuration = rememberMe ? AppConstants.Auth.SESSION_TTL_REMEMBER_ME_MS : AppConstants.Auth.SESSION_TTL_DEFAULT_MS;
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

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public long getLastActiveMillis() {
        return lastActiveMillis;
    }

    public void setLastActiveMillis(long lastActiveMillis) {
        this.lastActiveMillis = lastActiveMillis;
    }
}
