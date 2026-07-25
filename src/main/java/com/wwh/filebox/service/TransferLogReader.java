package com.wwh.filebox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwh.filebox.model.TransferDirection;
import com.wwh.filebox.model.TransferRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 读取传输日志(JSONL),按时间倒序、分页、按条件筛选,供后台日志页使用。
 * Reads the transfer log (JSONL), newest-first, paginated, filtered — for the admin log page.
 */
public class TransferLogReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;

    public TransferLogReader(Path file) {
        this.file = file;
    }

    /**
     * 按筛选条件读取一页(最新在前)。offset/limit 为对"筛选+排序"后结果的切片。
     * Read one page (newest first) matching the filter; offset/limit slice the filtered+sorted result.
     */
    /**
     * 全部满足筛选条件的记录(最新在前)。统计汇总基于此(整个筛选集),分页只是对其切片。
     * All records matching the filter (newest first). Stats are computed over this full set;
     * pagination merely slices it.
     */
    public List<TransferRecord> readAll(LogFilter filter) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        List<TransferRecord> filtered = new ArrayList<>();
        for (TransferRecord record : parseAll()) {
            if (matches(record, filter)) {
                filtered.add(record);
            }
        }
        filtered.sort(Comparator.comparingLong(TransferRecord::getTimeMillis).reversed());
        return filtered;
    }

    public LogPage read(LogFilter filter, int offset, int limit) {
        List<TransferRecord> filtered = readAll(filter);
        int total = filtered.size();
        int from = Math.min(Math.max(offset, 0), total);
        int to = Math.min(from + Math.max(limit, 0), total);
        return new LogPage(new ArrayList<>(filtered.subList(from, to)), total);
    }

    /** 解析全部行,跳过空行与坏行(不因单条损坏拖垮整个读取)。/ parse all lines, skipping blank/malformed ones. */
    private List<TransferRecord> parseAll() {
        List<TransferRecord> out = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return out; // 读失败按空处理 / treat unreadable as empty
        }
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            try {
                out.add(MAPPER.readValue(line, TransferRecord.class));
            } catch (IOException ignored) {
                // 跳过坏行 / skip a malformed line
            }
        }
        return out;
    }

    private boolean matches(TransferRecord r, LogFilter f) {
        if (f.direction != null && r.getDirection() != f.direction) {
            return false;
        }
        if (f.user != null && !f.user.equals(r.getUser())) {
            return false;
        }
        if (f.space != null && !f.space.equals(r.getSpace())) {
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

    /** 筛选条件,各字段为 null 表示不限制 / filter criteria; null means "no constraint". */
    public static final class LogFilter {
        public final TransferDirection direction;
        public final String user;
        public final String space;
        public final Long fromMillis;
        public final Long toMillis;

        public LogFilter(TransferDirection direction, String user, String space, Long fromMillis, Long toMillis) {
            this.direction = direction;
            this.user = user;
            this.space = space;
            this.fromMillis = fromMillis;
            this.toMillis = toMillis;
        }
    }

    /** 一页结果:切片后的记录 + 切片前总数(用于分页"是否还有更多")。/ a page: sliced records + pre-slice total. */
    public static final class LogPage {
        public final List<TransferRecord> records;
        public final int total;

        public LogPage(List<TransferRecord> records, int total) {
            this.records = records;
            this.total = total;
        }
    }
}
