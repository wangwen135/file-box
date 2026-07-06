package com.wwh.filebox.service;

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

/**
 * Builds a lightweight, rebuildable view of files in one storage space.
 * The catalog is derived solely from filesystem metadata; it is not persisted or cached.
 */
@Service
public class FileCatalogService {

    public ScanResult scan(Path root, String year, String month, int limit) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot) || !Files.isDirectory(normalizedRoot)) {
            return new ScanResult(Collections.emptyList(), Collections.emptyList());
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
        List<FileEntry> filtered = new ArrayList<>();
        for (FileEntry entry : allFiles) {
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
        if (filtered.size() > limit) {
            filtered = new ArrayList<>(filtered.subList(0, limit));
        }
        return new ScanResult(filtered, periods);
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

        ScanResult(List<FileEntry> files, List<Map<String, Object>> periods) {
            this.files = files;
            this.periods = periods;
        }

        public List<FileEntry> getFiles() { return files; }
        public List<Map<String, Object>> getPeriods() { return periods; }
    }
}
