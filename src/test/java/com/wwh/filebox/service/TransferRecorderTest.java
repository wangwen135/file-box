package com.wwh.filebox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwh.filebox.model.TransferDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TransferRecorderTest {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<Map<String, Object>>() {};

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;
    private ActiveTransferRegistry registry;
    private JsonlLog log;
    private TransferRecorder recorder;

    @BeforeEach
    void setup() {
        registry = new ActiveTransferRegistry();
        log = new JsonlLog(tempDir.resolve("transfer.jsonl"), 1000);
        recorder = new TransferRecorder(registry, log);
    }

    @Test
    void completeRegistersDuringTransferAndLogsSuccess() throws Exception {
        TransferRecorder.Handle h = recorder.begin(TransferDirection.UPLOAD, "admin", "默认", "dir/a.txt", "1.2.3.4");

        // 进行中:活跃表有一条 / in flight: one active entry
        assertThat(registry.snapshot()).hasSize(1);

        h.complete(1024);

        // 结束后:活跃表清空,日志落一条成功记录 / done: registry empty, one SUCCESS record logged
        assertThat(registry.snapshot()).isEmpty();
        List<String> lines = Files.readAllLines(log.getFile());
        assertThat(lines).hasSize(1);
        Map<String, Object> rec = mapper.readValue(lines.get(0), MAP);
        assertThat(rec.get("result")).isEqualTo("SUCCESS");
        assertThat(rec.get("direction")).isEqualTo("UPLOAD");
        assertThat(rec.get("user")).isEqualTo("admin");
        assertThat(rec.get("file")).isEqualTo("dir/a.txt");
        assertThat(((Number) rec.get("size")).longValue()).isEqualTo(1024);
        assertThat(((Number) rec.get("durationMillis")).longValue()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void failLogsFailedWithZeroBytes() throws Exception {
        TransferRecorder.Handle h = recorder.begin(TransferDirection.DOWNLOAD, "alice", "共享", "b.txt", "5.6.7.8");
        h.fail();

        assertThat(registry.snapshot()).isEmpty();
        Map<String, Object> rec = mapper.readValue(Files.readAllLines(log.getFile()).get(0), MAP);
        assertThat(rec.get("result")).isEqualTo("FAILED");
        assertThat(((Number) rec.get("size")).longValue()).isZero();
        assertThat(rec.get("direction")).isEqualTo("DOWNLOAD");
    }

    @Test
    void loggingFailureMustNotPropagate() throws Exception {
        // 把日志文件指向"一个普通文件之下",使写入必然失败 / point the log under a regular file so writes fail
        Path blocker = tempDir.resolve("blocker");
        Files.createFile(blocker);
        JsonlLog badLog = new JsonlLog(blocker.resolve("inside.jsonl"), 100);
        TransferRecorder r = new TransferRecorder(registry, badLog);

        TransferRecorder.Handle h = r.begin(TransferDirection.UPLOAD, "admin", "s", "f.txt", "1.2.3.4");

        // 写日志失败也不得抛异常,且活跃表仍正常注销 / must not throw, registry still deregistered
        assertThatCode(() -> h.complete(10)).doesNotThrowAnyException();
        assertThat(registry.snapshot()).isEmpty();
    }
}
