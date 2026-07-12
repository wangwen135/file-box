package com.wwh.filebox.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 目录视图文件排序的单元测试 / Unit tests for directory-view file ordering.
 * 验证 /list_dir 返回的文件按 mtime 倒序(最新在前),同时间按文件名做 tiebreaker。
 */
class FileBoxControllerSortTest {

    private static Map<String, Object> rec(String name, long timeMillis) {
        Map<String, Object> m = new HashMap<>();
        m.put("filename", name);
        m.put("timeMillis", timeMillis);
        return m;
    }

    @Test
    @DisplayName("按 mtime 倒序排列(最新在最前) / newest mtime first")
    void sortsByTimeDescending() {
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(rec("old.txt", 1_000L));
        files.add(rec("newest.mp4", 3_000L));
        files.add(rec("mid.txt", 2_000L));

        FileBoxController.sortDirRecordsByTimeDesc(files);

        assertThat(files).extracting(m -> m.get("filename"))
                .containsExactly("newest.mp4", "mid.txt", "old.txt");
    }

    @Test
    @DisplayName("同 mtime 按文件名做 tiebreaker,保证分页稳定 / filename tiebreaker keeps paging stable")
    void sameMtimeUsesFilenameTiebreaker() {
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(rec("banana.txt", 5_000L));
        files.add(rec("apple.txt", 5_000L));
        files.add(rec("cherry.txt", 5_000L));

        FileBoxController.sortDirRecordsByTimeDesc(files);

        // 相同时间下顺序确定,不随调用变化 / deterministic order when mtime ties
        assertThat(files).extracting(m -> m.get("filename"))
                .containsExactly("apple.txt", "banana.txt", "cherry.txt");
    }

    @Test
    @DisplayName("大小写不敏感的文件名 tiebreaker / case-insensitive filename tiebreaker")
    void filenameTiebreakerIsCaseInsensitive() {
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(rec("Beta.txt", 5_000L));
        files.add(rec("alpha.txt", 5_000L));

        FileBoxController.sortDirRecordsByTimeDesc(files);

        assertThat(files).extracting(m -> m.get("filename"))
                .containsExactly("alpha.txt", "Beta.txt");
    }

    @Test
    @DisplayName("未指定上传目录时按年月归档 / omitted upload target uses year and month")
    void omittedUploadTargetUsesYearAndMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.JULY, 12);

        assertThat(FileBoxController.resolveUploadTargetFolder(null, calendar.getTime()))
                .isEqualTo("2026/07");
    }

    @Test
    @DisplayName("显式空目录仍表示存储空间根目录 / explicit empty target keeps storage root")
    void explicitEmptyUploadTargetKeepsRoot() {
        assertThat(FileBoxController.resolveUploadTargetFolder("", new Date()))
                .isEmpty();
    }
}
