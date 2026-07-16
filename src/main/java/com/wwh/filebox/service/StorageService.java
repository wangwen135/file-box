package com.wwh.filebox.service;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.model.StorageSpace;
import com.wwh.filebox.model.StorageStats;
import com.wwh.filebox.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage service
 * 存储服务
 *
 * <p>提供存储空间的管理功能，包括：</p>
 * <ul>
 *   <li>存储空间的增删改查</li>
 *   <li>存储空间统计信息（使用量、文件数等）</li>
 *   <li>统计信息缓存（5分钟TTL）</li>
 *   <li>存储空间名称和URL前缀验证</li>
 * </ul>
 *
 * <p>缓存策略：</p>
 * <ul>
 *   <li>统计信息缓存5分钟，减少磁盘IO</li>
 *   <li>使用ConcurrentHashMap保证线程安全</li>
 * </ul>
 */
@Service
public class StorageService {

    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

    @Autowired
    private ConfigService configService;

    private final Map<String, StorageStats> statsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastStatsUpdate = new ConcurrentHashMap<>();

    /**
     * Get all storage spaces
     */
    public List<StorageSpace> getAllStorageSpaces() {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getStorageSpaces() == null) {
            return new ArrayList<>();
        }

        return config.getStorageSpaces().stream()
                .map(spaceConfig -> {
                    StorageSpace space = new StorageSpace();
                    space.setName(spaceConfig.getName());
                    space.setPath(spaceConfig.getPath());
                    space.setAllowAnonymousAccess(spaceConfig.isAllowAnonymousAccess());
                    space.setAllowAnonymousUpload(spaceConfig.isAllowAnonymousUpload());
                    return space;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get storage space by name
     */
    public StorageSpace getStorageSpace(String name) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getStorageSpaces() == null) {
            return null;
        }

        for (SystemConfig.StorageSpaceConfig spaceConfig : config.getStorageSpaces()) {
            if (spaceConfig.getName().equals(name)) {
                StorageSpace space = new StorageSpace();
                space.setName(spaceConfig.getName());
                space.setPath(spaceConfig.getPath());
                space.setMaxSize(spaceConfig.getMaxSize());
                space.setAllowAnonymousAccess(spaceConfig.isAllowAnonymousAccess());
                space.setAllowAnonymousUpload(spaceConfig.isAllowAnonymousUpload());
                return space;
            }
        }

        return null;
    }

    /**
     * Validate storage space name
     */
    private boolean isValidStorageSpaceName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        // Only allow letters, numbers, underscore, hyphen, and Chinese characters
        return name.matches(AppConstants.Storage.STORAGE_NAME_PATTERN);
    }

    /**
     * 改名判定：新名非空、去空白后与旧名不同，才视为改名。
     * 与 {@link AdminController} 及在线会话迁移共用同一套定义，避免判定不一致。
     * Decide whether an update is actually a rename: new name present and, once
     * trimmed, different from the old name. Shared by the controller and the
     * in-memory session migration so both agree on what "renamed" means.
     */
    public static boolean shouldRename(String oldName, String rawNewName) {
        String trimmed = rawNewName == null ? "" : rawNewName.trim();
        return !trimmed.isEmpty() && !trimmed.equals(oldName);
    }

    /**
     * 校验并准备存储目录：不存在则创建；无法创建或被普通文件占用则抛 IllegalStateException。
     * Validate and prepare the storage directory: create it if missing; throw if it cannot be
     * created or is occupied by a regular file. 注意 File.mkdirs() 失败时只返回 false、不抛异常，
     * 必须显式检查返回值 —— 原先的 try/catch 是死代码，目录建不出来时配置仍会被保存。
     */
    private File ensureStorageDirectory(String path) {
        File storageDir = new File(path);
        if (storageDir.exists()) {
            if (!storageDir.isDirectory()) {
                // 路径被普通文件占用，无法作为目录使用 / a regular file blocks the path
                throw new IllegalStateException("路径已被文件占用，无法作为存储目录：" + path);
            }
            return storageDir;
        }
        // mkdirs() 失败时只返回 false 而非抛异常，必须检查返回值 / mkdirs() returns false on failure
        boolean created = storageDir.mkdirs();
        if (!created || !storageDir.isDirectory()) {
            throw new IllegalStateException("无法创建存储目录：" + path + "（请检查路径是否正确且有写入权限）");
        }
        logger.info("Created storage directory: {}", path);
        return storageDir;
    }

    /**
     * Create storage space
     */
    public boolean createStorageSpace(String name, String path, String maxSize, boolean allowAnonymousAccess, boolean allowAnonymousUpload) {
        SystemConfig config = configService.getConfig();
        if (config == null) {
            return false;
        }

        // Validate name
        if (!isValidStorageSpaceName(name)) {
            logger.warn("Invalid storage space name: {}", name);
            return false;
        }

        // Check if storage space already exists
        if (getStorageSpace(name) != null) {
            logger.warn("Storage space {} already exists", name);
            return false;
        }

        // Validate path; ensureStorageDirectory creates the dir if missing and throws if it can't
        if (path == null || path.trim().isEmpty()) {
            logger.warn("Path is empty");
            return false;
        }
        ensureStorageDirectory(path);

        SystemConfig.StorageSpaceConfig spaceConfig = new SystemConfig.StorageSpaceConfig();
        spaceConfig.setName(name);
        spaceConfig.setPath(path);
        spaceConfig.setMaxSize(maxSize);
        // 上传蕴含访问 / upload implies access
        spaceConfig.setAllowAnonymousAccess(allowAnonymousAccess || allowAnonymousUpload);
        spaceConfig.setAllowAnonymousUpload(allowAnonymousUpload);

        if (config.getStorageSpaces() == null) {
            config.setStorageSpaces(new ArrayList<>());
        }
        config.getStorageSpaces().add(spaceConfig);

        // Automatically assign new storage space to all admin users
        if (config.getUsers() != null) {
            for (SystemConfig.UserConfig user : config.getUsers()) {
                if ("ADMIN".equals(user.getRole()) && !user.getStorageSpaces().contains(name)) {
                    user.getStorageSpaces().add(name);
                    logger.info("Automatically assigned storage space {} to admin user {}", name, user.getUsername());
                }
            }
        }

        configService.saveConfig(config);

        logger.info("Storage space {} created", name);
        return true;
    }

    /**
     * Update storage space
     */
    public boolean updateStorageSpace(String name, String newName, String path, String maxSize, boolean allowAnonymousAccess, boolean allowAnonymousUpload) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getStorageSpaces() == null) {
            return false;
        }

        // Validate path; ensureStorageDirectory creates the dir if missing and throws if it can't
        if (path == null || path.trim().isEmpty()) {
            logger.warn("Path is empty");
            return false;
        }
        ensureStorageDirectory(path);

        // 改名判定：新名非空且与旧名不同才触发改名 / rename only when new name is non-blank and differs
        boolean rename = shouldRename(name, newName);
        String trimmedNew = newName != null ? newName.trim() : "";

        if (rename) {
            // 新名走与新建相同的正则校验（允许中文/字母/数字/下划线/连字符）
            if (!isValidStorageSpaceName(trimmedNew)) {
                logger.warn("Invalid storage space name: {}", trimmedNew);
                return false;
            }
            // 重名检查（排除正在改名的自身）/ duplicate check excluding the space being renamed
            for (SystemConfig.StorageSpaceConfig other : config.getStorageSpaces()) {
                if (!other.getName().equals(name) && other.getName().equals(trimmedNew)) {
                    logger.warn("Storage space {} already exists", trimmedNew);
                    return false;
                }
            }
        }

        for (SystemConfig.StorageSpaceConfig spaceConfig : config.getStorageSpaces()) {
            if (spaceConfig.getName().equals(name)) {
                if (rename) {
                    // 改名：更新空间名 + 同步所有用户引用 + 清旧名统计缓存（懒重建）
                    // Rename: set the new name, sync every user's reference, drop stale stats cache
                    spaceConfig.setName(trimmedNew);
                    if (config.getUsers() != null) {
                        for (SystemConfig.UserConfig user : config.getUsers()) {
                            if (user.getStorageSpaces() != null) {
                                Collections.replaceAll(user.getStorageSpaces(), name, trimmedNew);
                            }
                        }
                    }
                    statsCache.remove(name);
                    lastStatsUpdate.remove(name);
                }

                spaceConfig.setPath(path);
                spaceConfig.setMaxSize(maxSize);
                // 上传蕴含访问 / upload implies access
                spaceConfig.setAllowAnonymousAccess(allowAnonymousAccess || allowAnonymousUpload);
                spaceConfig.setAllowAnonymousUpload(allowAnonymousUpload);

                configService.saveConfig(config);
                logger.info("Storage space {} updated{}", name, rename ? " (renamed to " + trimmedNew + ")" : "");
                return true;
            }
        }

        return false;
    }

    /**
     * Delete storage space
     */
    public boolean deleteStorageSpace(String name) {
        SystemConfig config = configService.getConfig();
        if (config == null || config.getStorageSpaces() == null) {
            return false;
        }

        boolean removed = config.getStorageSpaces().removeIf(space -> space.getName().equals(name));
        if (removed) {
            // 同步移除各用户对该存储空间的引用，否则用户列表里仍会展示已删除的空间
            // Remove the deleted space from every user's assigned list, otherwise the
            // user list keeps showing a storage space that no longer exists.
            if (config.getUsers() != null) {
                for (SystemConfig.UserConfig user : config.getUsers()) {
                    if (user.getStorageSpaces() != null) {
                        user.getStorageSpaces().removeIf(name::equals);
                    }
                }
            }

            configService.saveConfig(config);
            logger.info("Storage space {} deleted", name);

            // Clear cache
            statsCache.remove(name);
            lastStatsUpdate.remove(name);
        }

        return removed;
    }

    /**
     * Get storage statistics
     */
    public StorageStats getStorageStats(String name) {
        // Check cache (5 minute TTL)
        Long lastUpdate = lastStatsUpdate.get(name);
        if (lastUpdate != null && System.currentTimeMillis() - lastUpdate < AppConstants.Storage.STATS_CACHE_TTL_MS) {
            return statsCache.get(name);
        }

        StorageSpace space = getStorageSpace(name);
        if (space == null) {
            return null;
        }

        File storageDir = space.getStorageDirectory();
        if (!storageDir.exists()) {
            return null;
        }

        StorageStats stats = new StorageStats();
        stats.setStorageSpaceName(name);
        stats.setTotalSize(space.getMaxSizeInBytes());

        // Calculate usage
        long usedSize = calculateDirectorySize(storageDir);
        stats.setUsedSize(usedSize);
        stats.setFreeSize(space.getMaxSizeInBytes() - usedSize);

        // Count files and directories
        int[] counts = countFilesAndDirectories(storageDir);
        stats.setFileCount(counts[0]);
        stats.setDirectoryCount(counts[1]);

        stats.setUsagePercentage((double) usedSize / space.getMaxSizeInBytes() * 100);

        // Update cache
        statsCache.put(name, stats);
        lastStatsUpdate.put(name, System.currentTimeMillis());

        return stats;
    }

    /**
     * Calculate directory size recursively
     */
    private long calculateDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    /**
     * Count files and directories
     */
    private int[] countFilesAndDirectories(File dir) {
        int fileCount = 0;
        int dirCount = 0;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileCount++;
                } else if (file.isDirectory()) {
                    dirCount++;
                    int[] subCounts = countFilesAndDirectories(file);
                    fileCount += subCounts[0];
                    dirCount += subCounts[1];
                }
            }
        }

        return new int[]{fileCount, dirCount};
    }

    /**
     * Check if storage space has enough space
     */
    public boolean hasEnoughSpace(String name, long fileSize) {
        StorageStats stats = getStorageStats(name);
        return stats != null && stats.getFreeSize() >= fileSize;
    }

    /**
     * Clear stats cache
     */
    public void clearStatsCache(String name) {
        if (name != null) {
            statsCache.remove(name);
            lastStatsUpdate.remove(name);
        } else {
            statsCache.clear();
            lastStatsUpdate.clear();
        }
    }

    /**
     * 应用内增删文件后增量调整缓存用量，避免下次读统计时全量遍历。
     * 冷缓存（statsCache 里无该空间）时直接 no-op —— 下次 getStorageStats() 自然全量遍历。
     * 不刷新 lastStatsUpdate，保留 5 分钟 TTL 的定期对账，防止应用外改动导致漂移。
     * Incrementally adjust cached usage after an app-driven file change, skipping a full
     * re-walk on the next stats read. No-op when the space has no cached stats yet.
     */
    public void adjustUsedSize(String name, long deltaBytes, int deltaFiles) {
        if (name == null) {
            return;
        }
        StorageStats stats = statsCache.get(name);
        if (stats == null) {
            return; // 冷缓存：留给下次 getStorageStats() 全量遍历 / cold cache: walk fresh next time
        }
        // long 非原子，并发上传时对同一实例加锁防丢失自增 / long is non-atomic; guard concurrent uploads
        synchronized (stats) {
            stats.setUsedSize(Math.max(0, stats.getUsedSize() + deltaBytes));
            long total = stats.getTotalSize();
            stats.setFreeSize(Math.max(0, total - stats.getUsedSize()));
            stats.setUsagePercentage(total > 0 ? (double) stats.getUsedSize() / total * 100 : 0);
            if (deltaFiles != 0) {
                stats.setFileCount(Math.max(0, stats.getFileCount() + deltaFiles));
            }
        }
    }

    /**
     * Get all storage stats
     */
    public List<StorageStats> getAllStorageStats() {
        List<StorageStats> allStats = new ArrayList<>();
        for (StorageSpace space : getAllStorageSpaces()) {
            StorageStats stats = getStorageStats(space.getName());
            if (stats != null) {
                allStats.add(stats);
            }
        }
        return allStats;
    }
}
