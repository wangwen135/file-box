package com.wwh.filebox.model;

import java.util.Arrays;

/**
 * User model
 * 用户模型
 */
public class User {

    private String username;
    private String password;
    private Role role;
    private String[] storageSpaces;
    private String currentStorageSpace;

    public User() {
    }

    public User(String username, String password, Role role, String[] storageSpaces) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.storageSpaces = storageSpaces;
        this.currentStorageSpace = storageSpaces != null && storageSpaces.length > 0 ? storageSpaces[0] : null;
    }

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

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", role=" + role +
                ", storageSpaces=" + Arrays.toString(storageSpaces) +
                ", currentStorageSpace='" + currentStorageSpace + '\'' +
                '}';
    }
}
