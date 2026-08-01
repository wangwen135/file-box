package com.wwh.filebox.model;

import com.wwh.filebox.config.FileBoxPaths;

import java.io.File;
import java.nio.file.Path;

/**
 * Storage space model
 * 存储空间模型
 */
public class StorageSpace {

    private String name;
    private String path;
    private String maxSizeStr; // String representation like "10GB"
    private long maxSize; // in bytes
    private boolean allowAnonymousAccess;   // 匿名访问(浏览) / anonymous access (browse)
    private boolean allowAnonymousUpload;   // 匿名上传(蕴含访问) / anonymous upload (implies access)

    public StorageSpace() {
        this.maxSize = 10L * 1024 * 1024 * 1024; // Default 10GB
        this.maxSizeStr = "10GB";
        this.allowAnonymousAccess = false;
        this.allowAnonymousUpload = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMaxSizeStr() {
        return maxSizeStr;
    }

    /**
     * 容量字符串(如 "10GB"),序列化为 JSON 的 {@code maxSize} 字段。
     * 前端 storage.html 读的就是 space.maxSize,而非 maxSizeStr。
     * / Capacity string (e.g. "10GB"), serialized as the JSON {@code maxSize} field,
     * which is what the storage.html frontend reads (not maxSizeStr).
     */
    public String getMaxSize() {
        return maxSizeStr;
    }

    public void setMaxSizeStr(String maxSizeStr) {
        this.maxSizeStr = maxSizeStr;
        this.maxSize = parseSize(maxSizeStr);
    }

    public long getMaxSizeInBytes() {
        return maxSize;
    }

    public void setMaxSize(String sizeStr) {
        this.maxSizeStr = sizeStr;
        this.maxSize = parseSize(sizeStr);
    }

    private long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return 10L * 1024 * 1024 * 1024; // Default 10GB
        }
        sizeStr = sizeStr.trim().toUpperCase();
        try {
            if (sizeStr.endsWith("GB")) {
                return Long.parseLong(sizeStr.replace("GB", "")) * 1024 * 1024 * 1024;
            } else if (sizeStr.endsWith("MB")) {
                return Long.parseLong(sizeStr.replace("MB", "")) * 1024 * 1024;
            } else if (sizeStr.endsWith("KB")) {
                return Long.parseLong(sizeStr.replace("KB", "")) * 1024;
            } else {
                return Long.parseLong(sizeStr);
            }
        } catch (NumberFormatException e) {
            return 10L * 1024 * 1024 * 1024; // Default 10GB
        }
    }

    public String getFormattedMaxSize() {
        return maxSizeStr != null ? maxSizeStr : formatBytes(maxSize);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return bytes + " B";
        }
    }

    public boolean isAllowAnonymousAccess() {
        return allowAnonymousAccess;
    }

    public void setAllowAnonymousAccess(boolean allowAnonymousAccess) {
        this.allowAnonymousAccess = allowAnonymousAccess;
    }

    public boolean isAllowAnonymousUpload() {
        return allowAnonymousUpload;
    }

    public void setAllowAnonymousUpload(boolean allowAnonymousUpload) {
        this.allowAnonymousUpload = allowAnonymousUpload;
    }

    /**
     * 存储根的绝对、规范化 {@link Path},相对路径按数据根({@link FileBoxPaths})解析。
     * Absolute, normalized storage root; relative paths resolve via the data home.
     * dataHome 默认为当前目录,故与旧 {@code new File(path)} 在 CWD 下结果一致(向后兼容)。
     */
    public Path getResolvedBasePath() {
        return FileBoxPaths.resolveRelOrAbs(path);
    }

    public File getStorageDirectory() {
        return getResolvedBasePath().toFile();
    }
}
