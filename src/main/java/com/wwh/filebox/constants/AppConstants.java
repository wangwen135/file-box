package com.wwh.filebox.constants;

public final class AppConstants {

    private AppConstants() {
    }

    public static final class Auth {
        public static final String DEFAULT_ADMIN_USERNAME = "admin";
        public static final int MIN_PASSWORD_LENGTH = 6;
        public static final String TOKEN_COOKIE_NAME = "token";

        // 会话有效期(毫秒):"记住我"=30 天,否则 1 天 / session TTL (ms): 30 days remembered, else 1 day
        public static final long SESSION_TTL_REMEMBER_ME_MS = 30L * 24 * 60 * 60 * 1000;
        public static final long SESSION_TTL_DEFAULT_MS = 24L * 60 * 60 * 1000;

        // Cookie 有效期(秒):需与对应会话有效期对齐 / cookie Max-Age (seconds), aligned with the matching session TTL
        public static final int TOKEN_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;   // 记住我 / remembered
        public static final int TOKEN_COOKIE_DEFAULT_AGE = 24 * 60 * 60;    // 不记住 / not remembered

        private Auth() {
        }

        /**
         * 按是否"记住我"选取 cookie 的 Max-Age(秒),使 cookie 寿命与对应的会话有效期一致。
         * Pick the cookie Max-Age (seconds) by the remember-me flag so the cookie lifetime
         * matches the corresponding session TTL.
         */
        public static int cookieMaxAgeSeconds(boolean rememberMe) {
            return rememberMe ? TOKEN_COOKIE_MAX_AGE : TOKEN_COOKIE_DEFAULT_AGE;
        }
    }

    public static final class LoginSecurity {
        public static final int MAX_ATTEMPTS = 5;
        public static final int TIME_WINDOW_MINUTES = 10;
        public static final int LOCK_TIME_MINUTES = 15;

        private LoginSecurity() {
        }
    }

    public static final class FileUpload {
        public static final String PASTE_FILE_PREFIX = "paste_";
        public static final String DEFAULT_FILE_EXTENSION = ".txt";
        public static final String PASTED_FILE_PREFIX = "pasted_";
        public static final int FILENAME_MAX_BYTES = 255;
        public static final int MAX_FILE_LIMIT = 1000;
        public static final int DEFAULT_FILE_OFFSET = 0;
        public static final int TEXT_PREVIEW_MAX_LENGTH = 300;
        public static final String MULTIPART_TEMP_DIR = "./runtime/multipart-tmp";
        public static final int DOWNLOAD_BUFFER_SIZE = 64 * 1024;

        private FileUpload() {
        }
    }

    public static final class Storage {
        public static final long STATS_CACHE_TTL_MS = 5L * 60 * 1000;
        public static final long SCAN_CACHE_TTL_MS = 45L * 1000;
        public static final int SCAN_CACHE_MAX_ENTRIES = 200_000;
        public static final String STORAGE_NAME_PATTERN = "^[\\u4e00-\\u9fa5a-zA-Z0-9_-]+$";

        private Storage() {
        }
    }

}
