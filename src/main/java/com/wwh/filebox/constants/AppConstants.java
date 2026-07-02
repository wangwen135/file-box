package com.wwh.filebox.constants;

/**
 * 应用常量定义
 * 统一管理应用中的各种常量，避免魔法数字和硬编码配置
 */
public final class AppConstants {

    private AppConstants() {
        // 私有构造函数，防止实例化
    }

    /**
     * 认证相关常量
     */
    public static final class Auth {
        /** 默认管理员用户名 */
        public static final String DEFAULT_ADMIN_USERNAME = "admin";

        /** 默认管理员密码 */
        public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

        /** 默认管理员密码的BCrypt哈希值 */
        public static final String DEFAULT_ADMIN_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        /** Token Cookie名称 */
        public static final String TOKEN_COOKIE_NAME = "token";

        /** Token默认有效期（30天，单位：秒） */
        public static final int TOKEN_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

        /** Session记住我的有效期（30天，单位：毫秒） */
        public static final long REMEMBER_ME_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;

        /** Session不记住我的有效期（1天，单位：毫秒） */
        public static final long SESSION_EXPIRY_MS = 24L * 60 * 60 * 1000;

        private Auth() {}
    }

    /**
     * 登录安全相关常量
     */
    public static final class LoginSecurity {
        /** 最大失败尝试次数 */
        public static final int MAX_ATTEMPTS = 5;

        /** 时间窗口（分钟） */
        public static final int TIME_WINDOW_MINUTES = 10;

        /** 锁定时间（分钟） */
        public static final int LOCK_TIME_MINUTES = 15;

        private LoginSecurity() {}
    }

    /**
     * 文件上传相关常量
     */
    public static final class FileUpload {
        /** 默认文本文件名前缀 */
        public static final String PASTE_FILE_PREFIX = "paste_";

        /** 默认文件扩展名 */
        public static final String DEFAULT_FILE_EXTENSION = ".txt";

        /** 粘贴文件名前缀 */
        public static final String PASTED_FILE_PREFIX = "pasted_";

        /** 重复文件后缀格式 */
        public static final String DUPLICATE_FILE_SUFFIX = " ({0})";

        /** 文件名最大长度 */
        public static final int MAX_FILENAME_LENGTH = 200;

        /** 文件列表默认限制 */
        public static final int DEFAULT_FILE_LIMIT = 50;

        /** 文件遍历最大深度 */
        public static final int MAX_TRAVERSE_DEPTH = 3;

        /** 文件内容预览最大长度（文本文件） */
        public static final int TEXT_PREVIEW_MAX_LENGTH = 300;

        private FileUpload() {}
    }

    /**
     * 存储相关常量
     */
    public static final class Storage {
        /** 存储统计缓存TTL（5分钟，单位：毫秒） */
        public static final long STATS_CACHE_TTL_MS = 5L * 60 * 1000;

        /** 存储空间名称正则表达式 */
        public static final String STORAGE_NAME_PATTERN = "^[\\u4e00-\\u9fa5a-zA-Z0-9_-]+$";

        private Storage() {}
    }

    /**
     * HTTP相关常量
     */
    public static final class Http {
        /** 默认内容类型 */
        public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

        /** 文本文件内容类型 */
        public static final String TEXT_CONTENT_TYPE = "text/plain; charset=UTF-8";

        private Http() {}
    }

    /**
     * UI相关常量
     */
    public static final class UI {
        /** 上传成功倒计时时间（毫秒） */
        public static final int UPLOAD_SUCCESS_COUNTDOWN_MS = 3000;

        /** 上传取消后延迟关闭时间（毫秒） */
        public static final int UPLOAD_CANCEL_DELAY_MS = 2000;

        /** 通知默认持续时间（毫秒） */
        public static final int NOTIFICATION_DEFAULT_DURATION = 3000;

        /** 通知最大数量 */
        public static final int NOTIFICATION_MAX_COUNT = 5;

        private UI() {}
    }

    /**
     * 系统相关常量
     */
    public static final class System {
        /** 应用名称 */
        public static final String APP_NAME = "File Box";

        /** 默认时区ID */
        public static final String DEFAULT_TIMEZONE_ID = "Asia/Shanghai";

        /** UTF-8字符集名称 */
        public static final String CHARSET_UTF8 = "UTF-8";

        private System() {}
    }
}
