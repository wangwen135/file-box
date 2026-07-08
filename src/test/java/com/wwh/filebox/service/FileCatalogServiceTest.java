package com.wwh.filebox.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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

        FileCatalogService.ScanResult result = service.scan(tempDir, "2025", "11", 0, 50);

        assertThat(result.getFiles()).extracting(FileCatalogService.FileEntry::getPath)
                .containsExactly(newer.toAbsolutePath().normalize());
        assertThat(result.getFiles()).extracting(FileCatalogService.FileEntry::getPath)
                .doesNotContain(older.toAbsolutePath().normalize());
        assertThat(result.getTotalFiltered()).isEqualTo(1);
        assertThat(result.getPeriods()).extracting(p -> p.get("year"))
                .containsExactly("2025", "2024");
    }

    @Test
    void treatsYearMonthDirectoryNamesAsOrdinaryPaths() throws Exception {
        Path file = write("2026/07/report.txt", "content", at(2023, 1, 15));

        FileCatalogService.ScanResult result = service.scan(tempDir, "2023", "01", 0, 50);

        assertThat(result.getFiles()).hasSize(1);
        assertThat(result.getFiles().get(0).getPath()).isEqualTo(file.toAbsolutePath().normalize());
        assertThat(result.getTotalFiltered()).isEqualTo(1);
        assertThat(result.getPeriods()).extracting(p -> p.get("year")).containsExactly("2023");
    }

    @Test
    void supportsWholeYearSortsNewestFirstAndAppliesLimit() throws Exception {
        write("a.txt", "a", at(2025, 1, 1));
        Path newest = write("中文目录/最新.txt", "b", at(2025, 12, 31));
        write("other.txt", "c", at(2024, 12, 31));

        FileCatalogService.ScanResult result = service.scan(tempDir, "2025", null, 0, 1);

        assertThat(result.getFiles()).hasSize(1);
        assertThat(result.getFiles().get(0).getPath()).isEqualTo(newest.toAbsolutePath().normalize());
        // total 是切片前的总数：2025 年有 2 个文件，即使 limit=1 只返回 1 个
        assertThat(result.getTotalFiltered()).isEqualTo(2);
        Map<String, Object> year2025 = result.getPeriods().get(0);
        assertThat(year2025.get("year")).isEqualTo("2025");
        assertThat((List<String>) year2025.get("months")).containsExactly("12", "01");
    }

    @Test
    void returnsEmptyResultForMissingOrEmptyRoot() throws Exception {
        assertThat(service.scan(tempDir.resolve("missing"), null, null, 0, 50).getFiles()).isEmpty();
        assertThat(service.scan(tempDir.resolve("missing"), null, null, 0, 50).getTotalFiltered()).isZero();
        assertThat(service.scan(tempDir, null, null, 0, 50).getPeriods()).isEmpty();
    }

    @Test
    void paginatesByOffsetAndKeepsStableOrderAcrossPages() throws Exception {
        // 同一年的 5 个文件，时间各异 / five files in one year with distinct mtimes
        write("f1.txt", "1", at(2025, 1, 1));
        write("f2.txt", "2", at(2025, 2, 1));
        write("f3.txt", "3", at(2025, 3, 1));
        write("f4.txt", "4", at(2025, 4, 1));
        write("f5.txt", "5", at(2025, 5, 1));

        FileCatalogService.ScanResult p1 = service.scan(tempDir, "2025", null, 0, 2);
        FileCatalogService.ScanResult p2 = service.scan(tempDir, "2025", null, 2, 2);
        FileCatalogService.ScanResult p3 = service.scan(tempDir, "2025", null, 4, 2);

        // 每页 total 恒定 / total is constant across pages
        assertThat(p1.getTotalFiltered()).isEqualTo(5);
        assertThat(p2.getTotalFiltered()).isEqualTo(5);
        assertThat(p3.getTotalFiltered()).isEqualTo(5);

        // 分页大小 2/2/1 / page sizes
        assertThat(p1.getFiles()).hasSize(2);
        assertThat(p2.getFiles()).hasSize(2);
        assertThat(p3.getFiles()).hasSize(1);

        // 三页拼起来等于一次性取全量，且顺序一致(mtime 倒序)，无重叠/遗漏
        // concatenated pages equal the full sorted list with no gaps or overlap
        FileCatalogService.ScanResult all = service.scan(tempDir, "2025", null, 0, 50);
        assertThat(all.getFiles()).hasSize(5);
        List<String> combined = new ArrayList<>();
        p1.getFiles().forEach(e -> combined.add(e.getPath().toString()));
        p2.getFiles().forEach(e -> combined.add(e.getPath().toString()));
        p3.getFiles().forEach(e -> combined.add(e.getPath().toString()));
        List<String> expected = new ArrayList<>();
        all.getFiles().forEach(e -> expected.add(e.getPath().toString()));
        assertThat(combined).containsExactlyElementsOf(expected);
    }

    @Test
    void offsetBeyondEndReturnsEmptyButTotalStillReported() throws Exception {
        write("f1.txt", "1", at(2025, 1, 1));
        write("f2.txt", "2", at(2025, 2, 1));

        FileCatalogService.ScanResult result = service.scan(tempDir, "2025", null, 100, 50);

        assertThat(result.getFiles()).isEmpty();
        // offset 越界返回空，但总数仍正确 / empty page, but total is still accurate
        assertThat(result.getTotalFiltered()).isEqualTo(2);
    }

    @Test
    void cacheServesStaleUntilInvalidated() throws Exception {
        write("a.txt", "a", at(2025, 1, 1));
        write("b.txt", "b", at(2025, 2, 1));

        // 首次扫描热缓存 / first scan warms the cache
        assertThat(service.scan(tempDir, "2025", null, 0, 50).getTotalFiltered()).isEqualTo(2);

        // 新增文件后，缓存未失效前仍返回旧值 / after adding a file, the stale cache is still served
        write("c.txt", "c", at(2025, 3, 1));
        assertThat(service.scan(tempDir, "2025", null, 0, 50).getTotalFiltered()).isEqualTo(2);

        // 失效后重新遍历，看到新文件 / after invalidation the new file becomes visible
        service.invalidateScanCache(tempDir);
        assertThat(service.scan(tempDir, "2025", null, 0, 50).getTotalFiltered()).isEqualTo(3);
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
