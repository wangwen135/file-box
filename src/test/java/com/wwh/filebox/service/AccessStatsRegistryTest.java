package com.wwh.filebox.service;

import com.wwh.filebox.model.AccessBucket;
import com.wwh.filebox.model.AccessStat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessStatsRegistryTest {

    private final AccessStatsRegistry r = new AccessStatsRegistry(1000);

    @Test
    void recordsAccumulatePerHourIp() {
        r.record("2026-07-25 14", "1.2.3.4", AccessBucket.LIST, 10, 1000L);
        r.record("2026-07-25 14", "1.2.3.4", AccessBucket.LIST, 20, 2000L);
        r.record("2026-07-25 14", "1.2.3.4", AccessBucket.DOWNLOAD, 100, 3000L);

        List<AccessStat> day = r.forDate("2026-07-25");
        assertThat(day).hasSize(1);
        AccessStat s = day.get(0);
        assertThat(s.getRequests()).isEqualTo(3);
        assertThat(s.getBytes()).isEqualTo(130);
        assertThat(s.getListRequests()).isEqualTo(2);
        assertThat(s.getDownloadRequests()).isEqualTo(1);
        assertThat(s.getOtherRequests()).isZero();
        assertThat(s.getLastSeenMillis()).isEqualTo(3000L);
    }

    @Test
    void separateIpsAreSeparate() {
        r.record("2026-07-25 14", "1.1.1.1", AccessBucket.LIST, 0, 1);
        r.record("2026-07-25 14", "2.2.2.2", AccessBucket.LIST, 0, 1);
        assertThat(r.forDate("2026-07-25")).hasSize(2);
    }

    @Test
    void forDateFiltersByDayAndMergesHoursSortedDesc() {
        r.record("2026-07-25 10", "1.1.1.1", AccessBucket.LIST, 0, 1);
        r.record("2026-07-25 22", "1.1.1.1", AccessBucket.DOWNLOAD, 0, 2);
        r.record("2026-07-26 01", "1.1.1.1", AccessBucket.LIST, 0, 3); // 次日,不计 / next day, excluded

        List<AccessStat> d25 = r.forDate("2026-07-25");
        assertThat(d25).hasSize(1);
        AccessStat s = d25.get(0);
        assertThat(s.getRequests()).isEqualTo(2); // 当天 2 个小时合并 / two hours merged
        assertThat(s.getListRequests()).isEqualTo(1);
        assertThat(s.getDownloadRequests()).isEqualTo(1);
    }

    @Test
    void forDateSortsByRequestCountDescending() {
        r.record("2026-07-25 10", "few", AccessBucket.LIST, 0, 1);
        r.record("2026-07-25 10", "many", AccessBucket.LIST, 0, 1);
        r.record("2026-07-25 11", "many", AccessBucket.LIST, 0, 1);
        r.record("2026-07-25 12", "many", AccessBucket.LIST, 0, 1);

        List<AccessStat> day = r.forDate("2026-07-25");
        assertThat(day.get(0).getIp()).isEqualTo("many");
        assertThat(day.get(1).getIp()).isEqualTo("few");
    }

    @Test
    void hourlyForIpReturns24BucketsZeroFilled() {
        r.record("2026-07-25 14", "1.1.1.1", AccessBucket.LIST, 0, 1);
        List<AccessStat> hours = r.hourlyForIp("2026-07-25", "1.1.1.1");
        assertThat(hours).hasSize(24);
        assertThat(hours.get(14).getRequests()).isEqualTo(1);
        assertThat(hours.get(0).getRequests()).isZero();
    }

    @Test
    void snapshotAndRestoreRoundTrip() {
        r.record("2026-07-25 14", "1.1.1.1", AccessBucket.LIST, 5, 100L);
        List<AccessStat> snap = r.snapshotData();

        AccessStatsRegistry r2 = new AccessStatsRegistry(1000);
        r2.restoreData(snap);

        AccessStat restored = r2.forDate("2026-07-25").get(0);
        assertThat(restored.getRequests()).isEqualTo(1);
        assertThat(restored.getBytes()).isEqualTo(5);
        assertThat(restored.getLastSeenMillis()).isEqualTo(100L);
    }

    @Test
    void trimBeforeDropsOlderHours() {
        r.record("2026-07-25 14", "1.1.1.1", AccessBucket.LIST, 0, 1);
        r.record("2026-07-26 14", "1.1.1.1", AccessBucket.LIST, 0, 1);
        r.trimBefore("2026-07-26"); // 丢弃 26 号之前的 / drop anything before the 26th
        assertThat(r.forDate("2026-07-25")).isEmpty();
        assertThat(r.forDate("2026-07-26")).hasSize(1);
    }
}
