package com.wwh.filebox.service;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.util.DateTimeFormatter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a lightweight, rebuildable view of files in one storage space.
 * The catalog is derived solely from filesystem metadata. The full tree walk is
 * cached briefly (see AppConstants.Storage.SCAN_CACHE_TTL_MS) so paginated list
 * requests reuse one walk; uploads/deletes invalidate it eagerly.
 *
 * 依据文件系统元数据构建轻量、可重建的文件视图。完整遍历结果做短时缓存，
 * 分页翻页复用同一次遍历，上传/删除时主动失效。
 */
@Service
public class FileCatalogService {

    /** 缓存一次完整遍历(allFiles + periods)及其获取时间 / one full walk + the moment it was fetched. */
    private static final class CachedWalk {
        final List<FileEntry> allFiles;
        final List<Map<String, Object>> periods;
        final long fetchedAt;

        CachedWalk(List<FileEntry> allFiles, List<Map<String, Object>> periods, long fetchedAt) {
            this.allFiles = allFiles;
            this.periods = periods;
            this.fetchedAt = fetchedAt;
        }
    }

    /** 按存储根(规范化绝对路径)缓存遍历结果 / cache keyed by normalized absolute storage root. */
    private final Map<Path, CachedWalk> walkCache = new ConcurrentHashMap<>();

    /**
     * 列出文件(按修改时间年/月过滤，按时间倒序)，并对结果做 offset/limit 分页。
     * List files filtered by mtime year/month, newest first, paginated by offset/limit.
     *
     * @param offset 分页偏移量(已排序结果中的起点) / page offset into the sorted result
     * @param limit  本页最多返回数量 / max items to return this page
     * @return 分页结果(含切片前的总数 totalFiltered，用于判断 hasMore)
     *         paginated result carrying the pre-slice total so callers can compute hasMore
     */
    public ScanResult scan(Path root, String year, String month, int offset, int limit) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        CachedWalk walked = walk(normalizedRoot);
        if (walked == null) {
            return new ScanResult(Collections.emptyList(), Collections.emptyList(), 0);
        }

        List<FileEntry> filtered = new ArrayList<>();
        for (FileEntry entry : walked.allFiles) {
            Date modified = new Date(entry.getLastModifiedMillis());
            if (year != null && !year.equals(DateTimeFormatter.formatYear(modified))) {
                continue;
            }
            if (month != null && !month.equals(DateTimeFormatter.formatMonth(modified))) {
                continue;
            }
            filtered.add(entry);
        }

        filtered.sort(Comparator.comparingLong(FileEntry::getLastModifiedMillis).reversed()
                .thenComparing(FileEntry::getRelativePath, String.CASE_INSENSITIVE_ORDER));

        int total = filtered.size();
        int from = Math.min(offset, total);
        int to = Math.min(offset + limit, total);
        List<FileEntry> page = from < to ? new ArrayList<>(filtered.subList(from, to)) : Collections.emptyList();
        return new ScanResult(page, walked.periods, total);
    }

    /**
     * 执行一次完整遍历并按存储根缓存；命中未过期的缓存则直接复用，避免分页时重复遍历整棵树。
     * Perform one full walk, cached per storage root; reuse a fresh cache entry so pagination
     * requests don't re-walk the whole tree every time.
     */
    private CachedWalk walk(Path normalizedRoot) throws IOException {
        if (!Files.exists(normalizedRoot) || !Files.isDirectory(normalizedRoot)) {
            return null;
        }
        long now = System.currentTimeMillis();
        CachedWalk cached = walkCache.get(normalizedRoot);
        if (cached != null && (now - cached.fetchedAt) <= AppConstants.Storage.SCAN_CACHE_TTL_MS) {
            return cached;
        }

        List<FileEntry> allFiles = new ArrayList<>();
        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(normalizedRoot) && Files.isSymbolicLink(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !Files.isSymbolicLink(file)) {
                    String relativePath = normalizedRoot.relativize(file.toAbsolutePath().normalize())
                            .toString().replace('\\', '/');
                    allFiles.add(new FileEntry(file.toAbsolutePath().normalize(), relativePath,
                            attrs.size(), attrs.lastModifiedTime().toMillis()));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        List<Map<String, Object>> periods = buildPeriods(allFiles);
        CachedWalk result = new CachedWalk(allFiles, periods, now);
        // 安全阀：文件过多则不入缓存，保护 -Xmx384m 堆 / safety cap: skip caching oversized trees
        if (allFiles.size() <= AppConstants.Storage.SCAN_CACHE_MAX_ENTRIES) {
            walkCache.put(normalizedRoot, result);
        }
        return result;
    }

    /**
     * 作废某存储空间的遍历缓存。上传/删除成功后由 controller 调用。
     * Invalidate the cached walk for a storage root; called by the controller after upload/delete.
     */
    public void invalidateScanCache(Path root) {
        if (root == null) {
            return;
        }
        walkCache.remove(root.toAbsolutePath().normalize());
    }

    private List<Map<String, Object>> buildPeriods(List<FileEntry> entries) {
        Map<String, List<String>> byYear = new TreeMap<>(Comparator.reverseOrder());
        for (FileEntry entry : entries) {
            Date modified = new Date(entry.getLastModifiedMillis());
            String year = DateTimeFormatter.formatYear(modified);
            String month = DateTimeFormatter.formatMonth(modified);
            List<String> months = byYear.computeIfAbsent(year, key -> new ArrayList<>());
            if (!months.contains(month)) {
                months.add(month);
            }
        }

        List<Map<String, Object>> periods = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byYear.entrySet()) {
            entry.getValue().sort(Comparator.reverseOrder());
            Map<String, Object> period = new LinkedHashMap<>();
            period.put("year", entry.getKey());
            period.put("months", entry.getValue());
            periods.add(period);
        }
        return periods;
    }

    public static final class FileEntry {
        private final Path path;
        private final String relativePath;
        private final long size;
        private final long lastModifiedMillis;

        FileEntry(Path path, String relativePath, long size, long lastModifiedMillis) {
            this.path = path;
            this.relativePath = relativePath;
            this.size = size;
            this.lastModifiedMillis = lastModifiedMillis;
        }

        public Path getPath() { return path; }
        public String getRelativePath() { return relativePath; }
        public long getSize() { return size; }
        public long getLastModifiedMillis() { return lastModifiedMillis; }
    }

    public static final class ScanResult {
        private final List<FileEntry> files;
        private final List<Map<String, Object>> periods;
        private final int totalFiltered;

        ScanResult(List<FileEntry> files, List<Map<String, Object>> periods) {
            this(files, periods, files.size());
        }

        ScanResult(List<FileEntry> files, List<Map<String, Object>> periods, int totalFiltered) {
            this.files = files;
            this.periods = periods;
            this.totalFiltered = totalFiltered;
        }

        public List<FileEntry> getFiles() { return files; }
        public List<Map<String, Object>> getPeriods() { return periods; }

        /** 切片前满足过滤条件的文件总数 / total files matching the filter, before offset/limit slicing. */
        public int getTotalFiltered() { return totalFiltered; }
    }
}
