package com.wwh.filebox.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用的 JSONL(每行一个 JSON)追加式存储,按"最近 N 条"做容量保留。
 * 传输记录、登录记录等审计日志共用此类,各自存自己的记录类型。
 * Generic append-only JSONL store (one JSON object per line) retaining only the most recent N
 * records. Shared by the transfer and login audit logs, each storing its own record type.
 */
public class JsonlLog {

    // ObjectMapper 线程安全,序列化只读使用 / thread-safe, used read-only for serialization
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private final int maxRecords;

    public JsonlLog(Path file, int maxRecords) {
        this.file = file;
        this.maxRecords = maxRecords;
    }

    public Path getFile() {
        return file;
    }

    /**
     * 追加一条记录:序列化为一行 JSON 并追加到文件末尾(文件或父目录不存在则自动创建),然后按需裁剪。
     * Append one record: serialize to a single JSON line, append to the file (creating it and
     * its parent directory if absent), then trim if needed.
     */
    public void append(Object record) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String line = MAPPER.writeValueAsString(record);
        Files.write(file, (line + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        trimIfNeeded();
    }

    /**
     * 超过 maxRecords 时丢弃最旧的记录,只保留最近 maxRecords 条(每次 append 后检查并按需重写整文件,
     * O(N) per append,对本应用预期的写入量可接受)。
     * Drop the oldest records so at most maxRecords remain. Checked and rewritten after every
     * append — O(N) per append, fine for this app's expected write volume.
     */
    private void trimIfNeeded() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() <= maxRecords) {
            return;
        }
        int from = lines.size() - maxRecords;
        List<String> kept = new ArrayList<>(lines.subList(from, lines.size()));
        String rewritten = String.join("\n", kept) + "\n";
        Files.write(file, rewritten.getBytes(StandardCharsets.UTF_8));
    }
}
