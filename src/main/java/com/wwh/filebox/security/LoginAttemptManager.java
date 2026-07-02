package com.wwh.filebox.security;

import com.wwh.filebox.constants.AppConstants;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录尝试服务，用于跟踪登录失败次数并实现账户锁定
 */
@Service
public class LoginAttemptManager {

    // 存储登录尝试信息的并发哈希表
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    /**
     * 记录登录失败
     *
     * @param username 用户名
     * @return 是否被锁定
     */
    public boolean loginFailed(String username) {
        LoginAttempt attempt = loginAttempts.computeIfAbsent(username, k -> new LoginAttempt());
        attempt.incrementAttempts();
        attempt.setLastAttemptTime(LocalDateTime.now());
        return isLocked(username);
    }

    /**
     * 记录登录成功，重置失败尝试次数
     *
     * @param username 用户名
     */
    public void loginSucceeded(String username) {
        loginAttempts.remove(username);
    }

    /**
     * 检查用户是否被锁定
     *
     * @param username 用户名
     * @return 是否被锁定
     */
    public boolean isLocked(String username) {
        LoginAttempt attempt = loginAttempts.get(username);
        if (attempt == null) {
            return false;
        }

        // 检查是否超过最大尝试次数
        if (attempt.getAttempts() < AppConstants.LoginSecurity.MAX_ATTEMPTS) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        // 检查锁定是否过期
        LocalDateTime lockExpiryTime = attempt.getLastAttemptTime().plusMinutes(AppConstants.LoginSecurity.LOCK_TIME_MINUTES);
        if (now.isAfter(lockExpiryTime)) {
            // 锁定已过期，清除记录
            loginAttempts.remove(username);
            return false;
        }

        // 检查最后一次尝试是否在时间窗口内
        LocalDateTime windowStartTime = now.minusMinutes(AppConstants.LoginSecurity.TIME_WINDOW_MINUTES);
        if (attempt.getLastAttemptTime().isBefore(windowStartTime)) {
            // 超过时间窗口，重置尝试次数
            attempt.resetAttempts();
            return false;
        }

        return true;
    }

    /**
     * 获取剩余锁定时间（秒）
     *
     * @param username 用户名
     * @return 剩余锁定时间（秒）
     */
    public long getRemainingLockTime(String username) {
        LoginAttempt attempt = loginAttempts.get(username);
        if (attempt == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockExpiryTime = attempt.getLastAttemptTime().plusMinutes(AppConstants.LoginSecurity.LOCK_TIME_MINUTES);

        if (now.isAfter(lockExpiryTime)) {
            return 0;
        }

        return java.time.Duration.between(now, lockExpiryTime).getSeconds();
    }

    /**
     * 内部类，用于存储登录尝试信息
     */
    private static class LoginAttempt {
        private int attempts = 0;
        private LocalDateTime lastAttemptTime;

        public int getAttempts() {
            return attempts;
        }

        public void incrementAttempts() {
            this.attempts++;
        }

        public void resetAttempts() {
            this.attempts = 0;
        }

        public LocalDateTime getLastAttemptTime() {
            return lastAttemptTime;
        }

        public void setLastAttemptTime(LocalDateTime lastAttemptTime) {
            this.lastAttemptTime = lastAttemptTime;
        }
    }
}