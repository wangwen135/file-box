package com.wwh.filebox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwh.filebox.model.LoginRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 读取登录日志(JSONL),按时间倒序、分页、按条件筛选,供后台登录日志页使用。
 * Reads the login log (JSONL), newest-first, paginated, filtered — for the admin login-log page.
 */
public class LoginLogReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;

    public LoginLogReader(Path file) {
        this.file = file;
    }

    public LoginPage read(LoginFilter filter, int offset, int limit) {
        if (!Files.exists(file)) {
            return new LoginPage(new ArrayList<>(), 0);
        }
        List<LoginRecord> filtered = new ArrayList<>();
        for (LoginRecord record : parseAll()) {
            if (matches(record, filter)) {
                filtered.add(record);
            }
        }
        filtered.sort(Comparator.comparingLong(LoginRecord::getTimeMillis).reversed());

        int total = filtered.size();
        int from = Math.min(Math.max(offset, 0), total);
        int to = Math.min(from + Math.max(limit, 0), total);
        return new LoginPage(new ArrayList<>(filtered.subList(from, to)), total);
    }

    private List<LoginRecord> parseAll() {
        List<LoginRecord> out = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return out;
        }
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            try {
                out.add(MAPPER.readValue(line, LoginRecord.class));
            } catch (IOException ignored) {
                // 跳过坏行 / skip a malformed line
            }
        }
        return out;
    }

    private boolean matches(LoginRecord r, LoginFilter f) {
        if (f.username != null && !f.username.equals(r.getUsername())) {
            return false;
        }
        if (f.success != null && r.isSuccess() != f.success) {
            return false;
        }
        if (f.fromMillis != null && r.getTimeMillis() < f.fromMillis) {
            return false;
        }
        if (f.toMillis != null && r.getTimeMillis() > f.toMillis) {
            return false;
        }
        return true;
    }

    /** 筛选条件,null 表示不限制 / filter criteria; null means "no constraint". */
    public static final class LoginFilter {
        public final String username;
        public final Boolean success;
        public final Long fromMillis;
        public final Long toMillis;

        public LoginFilter(String username, Boolean success, Long fromMillis, Long toMillis) {
            this.username = username;
            this.success = success;
            this.fromMillis = fromMillis;
            this.toMillis = toMillis;
        }
    }

    /** 一页结果:切片后的记录 + 切片前总数。/ a page: sliced records + pre-slice total. */
    public static final class LoginPage {
        public final List<LoginRecord> records;
        public final int total;

        public LoginPage(List<LoginRecord> records, int total) {
            this.records = records;
            this.total = total;
        }
    }
}
