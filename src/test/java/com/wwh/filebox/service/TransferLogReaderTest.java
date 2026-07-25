package com.wwh.filebox.service;

import com.wwh.filebox.model.TransferDirection;
import com.wwh.filebox.model.TransferRecord;
import com.wwh.filebox.model.TransferResult;
import com.wwh.filebox.service.TransferLogReader.LogFilter;
import com.wwh.filebox.service.TransferLogReader.LogPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferLogReaderTest {

    @TempDir
    Path tempDir;
    private JsonlLog log;
    private TransferLogReader reader;

    @BeforeEach
    void seed() throws Exception {
        Path file = tempDir.resolve("transfer-history.jsonl");
        log = new JsonlLog(file, 1000);
        reader = new TransferLogReader(file);
        // 时间递增 t1<t2<t3<t4,故最新为 t4 / ascending times, newest is t4
        log.append(rec(1, TransferDirection.UPLOAD, "admin", "默认", "a.txt", 100, TransferResult.SUCCESS, 100));
        log.append(rec(2, TransferDirection.DOWNLOAD, "alice", "共享", "b.txt", 200, TransferResult.SUCCESS, 50));
        log.append(rec(3, TransferDirection.UPLOAD, "admin", "默认", "c.txt", 300, TransferResult.FAILED, 10));
        log.append(rec(4, TransferDirection.UPLOAD, "alice", "共享", "d.txt", 400, TransferResult.SUCCESS, 400));
    }

    @Test
    void readsNewestFirstAndPaginates() {
        LogPage p1 = reader.read(noFilter(), 0, 2);
        assertThat(p1.total).isEqualTo(4);
        assertThat(files(p1)).containsExactly("d.txt", "c.txt");

        LogPage p2 = reader.read(noFilter(), 2, 2);
        assertThat(p2.total).isEqualTo(4);
        assertThat(files(p2)).containsExactly("b.txt", "a.txt");
    }

    @Test
    void filtersByDirectionUserSpaceAndTime() {
        // 仅上传 / uploads only: a,c,d → 最新在前 d,c,a
        LogPage uploads = reader.read(new LogFilter(TransferDirection.UPLOAD, null, null, null, null), 0, 50);
        assertThat(uploads.total).isEqualTo(3);
        assertThat(files(uploads)).containsExactly("d.txt", "c.txt", "a.txt");

        // admin 用户 / user admin: a,c → c,a
        LogPage admin = reader.read(new LogFilter(null, "admin", null, null, null), 0, 50);
        assertThat(admin.total).isEqualTo(2);
        assertThat(files(admin)).containsExactly("c.txt", "a.txt");

        // 空间 共享 / space 共享: b,d → d,b
        LogPage shared = reader.read(new LogFilter(null, null, "共享", null, null), 0, 50);
        assertThat(files(shared)).containsExactly("d.txt", "b.txt");

        // 时间区间 [t2,t3] 含两端 / time range [t2,t3] inclusive: b,c → c,b
        LogPage ranged = reader.read(new LogFilter(null, null, null, time(2), time(3)), 0, 50);
        assertThat(files(ranged)).containsExactly("c.txt", "b.txt");
    }

    @Test
    void returnsEmptyForMissingFile() {
        TransferLogReader missing = new TransferLogReader(tempDir.resolve("does-not-exist.jsonl"));
        LogPage page = missing.read(noFilter(), 0, 50);
        assertThat(page.records).isEmpty();
        assertThat(page.total).isZero();
    }

    private LogFilter noFilter() {
        return new LogFilter(null, null, null, null, null);
    }

    private List<String> files(LogPage page) {
        List<String> out = new ArrayList<>();
        for (TransferRecord r : page.records) {
            out.add(r.getFile());
        }
        return out;
    }

    private long time(int t) {
        return 1_700_000_000_000L + t;
    }

    private TransferRecord rec(int t, TransferDirection dir, String user, String space, String file,
                               long size, TransferResult result, long durationMillis) {
        return new TransferRecord(time(t), dir, user, space, file, size, result, "1.2.3.4", durationMillis);
    }
}
