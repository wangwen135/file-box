package com.wwh.filebox.util;

import javax.servlet.http.HttpServletRequest;

/**
 * 取客户端真实 IP:优先 X-Forwarded-For(反代场景取首段),否则回退到 remoteAddr。
 * Resolve the client's real IP: prefer X-Forwarded-For (first hop, for reverse proxies),
 * otherwise fall back to remoteAddr.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String from(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "";
    }
}
