package com.wwh.filebox.constants;

public final class AppConstants {

    private AppConstants() {
    }

    public static final class Auth {
        public static final String DEFAULT_ADMIN_USERNAME = "admin";
        public static final int MIN_PASSWORD_LENGTH = 6;
        public static final String TOKEN_COOKIE_NAME = "token";
        public static final int TOKEN_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;
        public static final long REMEMBER_ME_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;
        public static final long SESSION_EXPIRY_MS = 24L * 60 * 60 * 1000;

        private Auth() {
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
        public static final String DUPLICATE_FILE_SUFFIX = " ({0})";
        public static final int MAX_FILENAME_LENGTH = 200;
        public static final int FILENAME_MAX_BYTES = 255;
        public static final int DEFAULT_FILE_LIMIT = 50;
        public static final int MAX_FILE_LIMIT = 1000;
        public static final int DEFAULT_FILE_OFFSET = 0;
        public static final int MAX_TRAVERSE_DEPTH = 3;
        public static final int TEXT_PREVIEW_MAX_LENGTH = 300;
        public static final String MULTIPART_TEMP_DIR = "./runtime/multipart-tmp";
        public static final int DOWNLOAD_BUFFER_SIZE = 64 * 1024;
        public static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024 * 1024;

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

    public static final class Http {
        public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
        public static final String TEXT_CONTENT_TYPE = "text/plain; charset=UTF-8";

        private Http() {
        }
    }

    public static final class UI {
        public static final int UPLOAD_SUCCESS_COUNTDOWN_MS = 3000;
        public static final int UPLOAD_CANCEL_DELAY_MS = 2000;
        public static final int NOTIFICATION_DEFAULT_DURATION = 3000;
        public static final int NOTIFICATION_MAX_COUNT = 5;

        private UI() {
        }
    }

    public static final class System {
        public static final String APP_NAME = "File Box";
        public static final String DEFAULT_TIMEZONE_ID = "Asia/Shanghai";
        public static final String CHARSET_UTF8 = "UTF-8";

        private System() {
        }
    }
}
