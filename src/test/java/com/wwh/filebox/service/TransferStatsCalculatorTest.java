package com.wwh.filebox.service;

import com.wwh.filebox.model.TransferDirection;
import com.wwh.filebox.model.TransferRecord;
import com.wwh.filebox.model.TransferResult;
import com.wwh.filebox.model.TransferStats;
import com.wwh.filebox.model.TransferStats.GroupStat;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferStatsCalculatorTest {

    private final TransferStatsCalculator calc = new TransferStatsCalculator();

    @Test
    void computesTotalsCountsAndAverageSpeed() {
        // 上传 100B@1000ms、200B@2000ms;下载 500B@1000ms
        List<TransferRecord> records = Arrays.asList(
                rec(1, TransferDirection.UPLOAD, "admin", "默认", 100, 1000),
                rec(2, TransferDirection.UPLOAD, "admin", "默认", 200, 2000),
                rec(3, TransferDirection.DOWNLOAD, "alice", "共享", 500, 1000));

        TransferStats s = calc.compute(records);

        assertThat(s.uploadBytes).isEqualTo(300);
        assertThat(s.uploadCount).isEqualTo(2);
        assertThat(s.downloadBytes).isEqualTo(500);
        assertThat(s.downloadCount).isEqualTo(1);
        // 总字节 800 / 总耗时 4000ms=4s → 200 B/s / total bytes 800 over 4s → 200 B/s
        assertThat(s.avgSpeedBytesPerSecond).isEqualTo(200);
    }

    @Test
    void groupsByUserAndSpace() {
        List<TransferRecord> records = Arrays.asList(
                rec(1, TransferDirection.UPLOAD, "admin", "默认", 100, 1000),
                rec(2, TransferDirection.DOWNLOAD, "admin", "共享", 50, 500),
                rec(3, TransferDirection.UPLOAD, "alice", "共享", 200, 1000));

        TransferStats s = calc.compute(records);

        GroupStat admin = find(s.byUser, "admin");
        assertThat(admin.uploadBytes).isEqualTo(100);
        assertThat(admin.downloadBytes).isEqualTo(50);
        assertThat(admin.count).isEqualTo(2);

        GroupStat shared = find(s.bySpace, "共享");
        assertThat(shared.uploadBytes).isEqualTo(200);
        assertThat(shared.downloadBytes).isEqualTo(50);
        assertThat(shared.count).isEqualTo(2);
    }

    @Test
    void handlesEmptyAndZeroDurationWithoutDivideByZero() {
        TransferStats empty = calc.compute(Collections.emptyList());
        assertThat(empty.uploadBytes).isZero();
        assertThat(empty.avgSpeedBytesPerSecond).isZero();
        assertThat(empty.byUser).isEmpty();

        // 耗时为 0 不得除零 / zero duration must not divide by zero
        TransferStats zero = calc.compute(Arrays.asList(rec(1, TransferDirection.UPLOAD, "u", "sp", 100, 0)));
        assertThat(zero.avgSpeedBytesPerSecond).isZero();
    }

    private GroupStat find(List<GroupStat> groups, String name) {
        return groups.stream()
                .filter(g -> g.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing group: " + name));
    }

    private TransferRecord rec(long time, TransferDirection dir, String user, String space, long size, long durationMillis) {
        return new TransferRecord(time, dir, user, space, "f.txt", size, TransferResult.SUCCESS, "1.2.3.4", durationMillis);
    }
}
