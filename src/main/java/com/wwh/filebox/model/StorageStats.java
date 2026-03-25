package com.wwh.filebox.model;

/**
 * Storage statistics model
 * 存储统计模型
 */
public class StorageStats {

    private String storageSpaceName;
    private long totalSize;
    private long usedSize;
    private long freeSize;
    private double usagePercentage;
    private int fileCount;
    private int directoryCount;

    public String getStorageSpaceName() {
        return storageSpaceName;
    }

    public void setStorageSpaceName(String storageSpaceName) {
        this.storageSpaceName = storageSpaceName;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public long getUsedSize() {
        return usedSize;
    }

    public void setUsedSize(long usedSize) {
        this.usedSize = usedSize;
    }

    public long getFreeSize() {
        return freeSize;
    }

    public void setFreeSize(long freeSize) {
        this.freeSize = freeSize;
    }

    public double getUsagePercentage() {
        return usagePercentage;
    }

    public void setUsagePercentage(double usagePercentage) {
        this.usagePercentage = usagePercentage;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public int getDirectoryCount() {
        return directoryCount;
    }

    public void setDirectoryCount(int directoryCount) {
        this.directoryCount = directoryCount;
    }

    public String getFormattedUsedSize() {
        return formatBytes(usedSize);
    }

    public String getFormattedFreeSize() {
        return formatBytes(freeSize);
    }

    public String getFormattedTotalSize() {
        return formatBytes(totalSize);
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
}
