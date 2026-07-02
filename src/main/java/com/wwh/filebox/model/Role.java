package com.wwh.filebox.model;

/**
 * User role enum
 * 用户角色枚举
 */
public enum Role {

    ADMIN,
    MANAGER,
    USER;

    public static Role fromString(String role) {
        if (role == null) {
            return USER;
        }
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
