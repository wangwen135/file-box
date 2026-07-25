package com.wwh.filebox.util;

import com.wwh.filebox.model.AccessBucket;

/**
 * 把一个请求路径归类到访问统计桶,并判断是否为静态资源(静态资源不计入统计)。
 * Classifies a request path into an access bucket, and tells static assets (excluded from stats).
 */
public final class AccessStatsClassifier {

    private static final String[] STATIC_PREFIXES = {"/css/", "/js/", "/images/", "/lib/"};
    private static final String[] LIST_PATHS = {"/list_files", "/list_dir", "/list_periods"};
    private static final String DOWNLOAD_PATH = "/api/file";

    private AccessStatsClassifier() {
    }

    /** 静态资源(css/js/图片/lib/favicon)不计入统计。/ static assets aren't counted. */
    public static boolean isStatic(String path) {
        String p = stripQuery(path);
        if ("/favicon.ico".equals(p) || "/favicon.png".equals(p)) {
            return true;
        }
        for (String prefix : STATIC_PREFIXES) {
            if (p.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 归类:列表/下载/其它。/ classify as LIST / DOWNLOAD / OTHER. */
    public static AccessBucket classify(String path) {
        String p = stripQuery(path);
        for (String listPath : LIST_PATHS) {
            if (p.equals(listPath)) {
                return AccessBucket.LIST;
            }
        }
        if (p.equals(DOWNLOAD_PATH)) {
            return AccessBucket.DOWNLOAD;
        }
        return AccessBucket.OTHER;
    }

    private static String stripQuery(String path) {
        if (path == null) {
            return "";
        }
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }
}
