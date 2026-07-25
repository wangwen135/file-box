package com.wwh.filebox.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一次登录尝试的记录(成功或失败都记),序列化为登录日志里的一行 JSON。
 * A login-attempt record (success or failure), serialized as one JSONL line in the login log.
 */
public final class LoginRecord {

    private final long timeMillis;
    private final String username;     // 尝试登录的用户名 / the username attempted
    private final String ip;           // 客户端 IP / client IP
    private final boolean success;     // 是否成功 / whether login succeeded
    private final String reason;       // 失败原因,成功时为 null / failure reason, null on success
    private final boolean rememberMe;  // 是否勾选"记住我" / whether "remember me" was checked

    @JsonCreator
    public LoginRecord(
            @JsonProperty("timeMillis") long timeMillis,
            @JsonProperty("username") String username,
            @JsonProperty("ip") String ip,
            @JsonProperty("success") boolean success,
            @JsonProperty("reason") String reason,
            @JsonProperty("rememberMe") boolean rememberMe) {
        this.timeMillis = timeMillis;
        this.username = username;
        this.ip = ip;
        this.success = success;
        this.reason = reason;
        this.rememberMe = rememberMe;
    }

    public long getTimeMillis() { return timeMillis; }
    public String getUsername() { return username; }
    public String getIp() { return ip; }
    public boolean isSuccess() { return success; }
    public String getReason() { return reason; }
    public boolean isRememberMe() { return rememberMe; }
}
