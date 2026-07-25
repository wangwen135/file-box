package com.wwh.filebox.web;

import com.wwh.filebox.model.AccessBucket;
import com.wwh.filebox.service.AccessStatsRegistry;
import com.wwh.filebox.util.AccessStatsClassifier;
import com.wwh.filebox.util.ClientIp;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统计每次(非静态)请求:包住响应数字节,请求结束后按 (小时, IP, 桶) 累加进注册表。
 * 统计本身出错绝不能影响真实请求。
 * Counts every non-static request: wraps the response to count bytes, then after the request
 * records (hour, IP, bucket) into the registry. Counting must never affect the real request.
 */
@Component
public class AccessStatsFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

    private final AccessStatsRegistry registry;

    public AccessStatsFilter(AccessStatsRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 静态资源(css/js/图片)不计入统计 / static assets aren't counted
        if (AccessStatsClassifier.isStatic(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        CountingResponseWrapper wrapped = new CountingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            try {
                AccessBucket bucket = AccessStatsClassifier.classify(path);
                String hourKey = LocalDateTime.now().format(HOUR_FMT);
                registry.record(hourKey, ClientIp.from(request), bucket,
                        wrapped.getByteCount(), System.currentTimeMillis());
            } catch (Exception ignored) {
                // 统计失败不得影响已完成的请求 / a stats failure must not affect the completed request
            }
        }
    }
}
