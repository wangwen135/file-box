package com.wwh.filebox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwh.filebox.model.TransferDirection;
import com.wwh.filebox.model.TransferRecord;
import com.wwh.filebox.model.TransferResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证通用 JSONL 存储的追加与保留;用 TransferRecord 作为样本记录类型。
 * Verifies the generic JSONL store's append + retention, using TransferRecord as the sample type.
 */
class JsonlLogTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void appendsOneJsonLinePerRecord() throws Exception {
        Path file = tempDir.resolve("history.jsonl");
        JsonlLog log = new JsonlLog(file, 100);

        log.append(new TransferRecord(
                1_700_000_000_000L, TransferDirection.UPLOAD, "admin", "默认",
                "dir/a.txt", 1024L, TransferResult.SUCCESS, "1.2.3.4", 500L));

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(1);

        Map<String, Object> read = mapper.readValue(lines.get(0), new TypeReference<Map<String, Object>>() {});
        assertThat(read.get("direction")).isEqualTo("UPLOAD");
        assertThat(read.get("user")).isEqualTo("admin");
        assertThat(read.get("space")).isEqualTo("默认");
        assertThat(read.get("file")).isEqualTo("dir/a.txt");
        assertThat(((Number) read.get("size")).longValue()).isEqualTo(1024L);
        assertThat(read.get("result")).isEqualTo("SUCCESS");
        assertThat(read.get("ip")).isEqualTo("1.2.3.4");
        assertThat(((Number) read.get("durationMillis")).longValue()).isEqualTo(500L);
        assertThat(((Number) read.get("timeMillis")).longValue()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void keepsOnlyTheMostRecentRecordsBeyondMax() throws Exception {
        Path file = tempDir.resolve("history.jsonl");
        JsonlLog log = new JsonlLog(file, 3);

        // 追加 5 条,容量 3 → 只应保留最近 3 条(file2/file3/file4) / append 5 into cap 3 -> last 3 kept
        for (int i = 0; i < 5; i++) {
            log.append(record("file" + i + ".txt", 1000L + i, 1_700_000_000_000L + i));
        }

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(3);
        assertThat(parseFile(lines.get(0))).isEqualTo("file2.txt");
        assertThat(parseFile(lines.get(1))).isEqualTo("file3.txt");
        assertThat(parseFile(lines.get(2))).isEqualTo("file4.txt");
    }

    private TransferRecord record(String filename, long size, long timeMillis) {
        return new TransferRecord(timeMillis, TransferDirection.UPLOAD, "admin", "默认",
                filename, size, TransferResult.SUCCESS, "1.2.3.4", 500L);
    }

    private String parseFile(String line) throws Exception {
        return (String) mapper.readValue(line, new TypeReference<Map<String, Object>>() {}).get("file");
    }
}
