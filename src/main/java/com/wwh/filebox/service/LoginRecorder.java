package com.wwh.filebox.service;

import com.wwh.filebox.model.LoginRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 记录一次登录尝试(成功/失败)到登录日志文件。写失败只记 warn,不影响登录本身。
 * Records a login attempt (success/failure) to the login-log file. A write failure is only warned
 * and never affects the login itself.
 */
public class LoginRecorder {

    private static final Logger logger = LoggerFactory.getLogger(LoginRecorder.class);

    private final JsonlLog loginLog;

    public LoginRecorder(JsonlLog loginLog) {
        this.loginLog = loginLog;
    }

    public void record(String username, String ip, boolean success, String reason, boolean rememberMe) {
        try {
            loginLog.append(new LoginRecord(System.currentTimeMillis(), username, ip, success, reason, rememberMe));
        } catch (IOException e) {
            logger.warn("Failed to append login record ({}): {}", username, e.getMessage());
        }
    }
}
