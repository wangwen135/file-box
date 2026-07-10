package com.wwh.filebox.security;

import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.SystemConfig;
import com.wwh.filebox.service.AuthService;
import com.wwh.filebox.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Authentication interceptor
 * 认证拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AuthInterceptor.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private ConfigService configService;

    // Paths that don't require authentication
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/anonymous-login",
            "/api/auth/logout",
            "/api/auth/config",
            "/api/auth/anonymous-config",
            "/login.html",
            "/login",
            "/logout",
            "/images/**",
            "/favicon.ico",
            "/favicon.png",
            "/css/**",
            "/js/**",
            "/lib/**"
    };

    // Paths that require admin role
    private static final String[] ADMIN_PATHS = {
            "/admin/dashboard.html",
            "/admin/storage.html",
            "/admin/users.html",
            "/admin/settings.html",
            "/api/admin/"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Request: {} {}", method, path);

        // Check if path is public
        if (isPublicPath(path)) {
            return true;
        }

        // Check if config file exists
        if (!configService.configExists()) {
            // No config file, allow access to login page
            if (path.equals("/login.html") || path.equals("/login")) {
                return true;
            }
            // Redirect to login page
            response.sendRedirect("/login.html");
            return false;
        }

        // Get session token from cookie
        String token = getTokenFromRequest(request);

        if (token == null) {
            // Redirect to login page for HTML requests
            if (path.endsWith(".html")) {
                response.sendRedirect("/login.html");
                return false;
            }
            // Return 401 for API requests
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"error_cn\":\"未授权\"}");
            return false;
        }

        LoginSession session = authService.getSession(token);
        if (session == null) {
            if (path.endsWith(".html")) {
                response.sendRedirect("/login.html");
                return false;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Invalid or expired token\",\"error_cn\":\"令牌无效或已过期\"}");
            return false;
        }

        // Check admin role for admin paths
        if (isAdminPath(path) && !Role.ADMIN.equals(session.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Forbidden\",\"error_cn\":\"禁止访问\"}");
            return false;
        }

        // Add session info to request
        request.setAttribute("session", session);
        request.setAttribute("storageSpace", session.getCurrentStorageSpace());

        return true;
    }

    /**
     * Check if path is public (doesn't require authentication)
     */
    private boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if path requires admin role
     */
    private boolean isAdminPath(String path) {
        for (String adminPath : ADMIN_PATHS) {
            if (path.startsWith(adminPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract authentication token from request
     * Priority: Cookie > Authorization header > Query parameter
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // Check cookie first (优先从Cookie获取)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Check Authorization header (检查Authorization header)
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }

        // Check query parameter (检查查询参数)
        token = request.getParameter("token");
        if (token != null) {
            return token;
        }

        return null;
    }
}
