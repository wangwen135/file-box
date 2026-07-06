package com.wwh.filebox.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileCatalogServiceTest {

    private final FileCatalogService service = new FileCatalogService();

    @TempDir
    Path tempDir;

    @Test
    void scansArbitraryDepthAndFiltersByModificationTime() throws Exception {
        Path older = write("项目/深层/旧文件.txt", "old", at(2024, 3, 2));
        Path newer = write("任意目录/新文件.txt", "new", at(2025, 11, 8));

        FileCatalogService.ScanResult result = service.scan(tempDir, "2025", "11", 50);

        assertThat(result.getFiles()).extracting(FileCatalogService.FileEntry::getPath)
                .containsExactly(newer.toAbsolutePath().normalize());
        assertThat(result.getFiles()).extracting(FileCatalogService.FileEntry::getPath)
                .doesNotContain(older.toAbsolutePath().normalize());
        assertThat(result.getPeriods()).extracting(p -> p.get("year"))
                .containsExactly("2025", "2024");
    }

    @Test
    void treatsYearMonthDirectoryNamesAsOrdinaryPaths() throws Exception {
        Path file = write("2026/07/report.txt", "content", at(2023, 1, 15));

        FileCatalogService.ScanResult result = service.scan(tempDir, "2023", "01", 50);

        assertThat(result.getFiles()).hasSize(1);
        assertThat(result.getFiles().get(0).getPath()).isEqualTo(file.toAbsolutePath().normalize());
        assertThat(result.getPeriods()).extracting(p -> p.get("year")).containsExactly("2023");
    }

    @Test
    void supportsWholeYearSortsNewestFirstAndAppliesLimit() throws Exception {
        write("a.txt", "a", at(2025, 1, 1));
        Path newest = write("中文目录/最新.txt", "b", at(2025, 12, 31));
        write("other.txt", "c", at(2024, 12, 31));

        FileCatalogService.ScanResult result = service.scan(tempDir, "2025", null, 1);

        assertThat(result.getFiles()).hasSize(1);
        assertThat(result.getFiles().get(0).getPath()).isEqualTo(newest.toAbsolutePath().normalize());
        Map<String, Object> year2025 = result.getPeriods().get(0);
        assertThat(year2025.get("year")).isEqualTo("2025");
        assertThat((List<String>) year2025.get("months")).containsExactly("12", "01");
    }

    @Test
    void returnsEmptyResultForMissingOrEmptyRoot() throws Exception {
        assertThat(service.scan(tempDir.resolve("missing"), null, null, 50).getFiles()).isEmpty();
        assertThat(service.scan(tempDir, null, null, 50).getPeriods()).isEmpty();
    }

    private Path write(String relative, String content, long modifiedMillis) throws Exception {
        Path file = tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(modifiedMillis));
        return file;
    }

    private long at(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 12, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
