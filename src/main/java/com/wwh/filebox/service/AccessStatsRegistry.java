package com.wwh.filebox.service;

import com.wwh.filebox.model.AccessBucket;
import com.wwh.filebox.model.AccessStat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的"访问统计"内存表:按 (小时, IP) 维度累计请求数、流量、桶分布。
 * 超过 maxEntries 个不同 (小时,IP) 后,新 IP 记进同小时的 "__other__" 桶,防止伪造 IP 撑爆内存。
 * Thread-safe in-memory access-stats table: per (hour, IP) accumulate requests, bytes, buckets.
 * Beyond maxEntries distinct (hour,IP), new IPs roll into an "__other__" bucket for that hour,
 * bounding memory under a spoofed-IP flood.
 */
public class AccessStatsRegistry {

    private static final String OVERFLOW_IP = "__other__";

    private final int maxEntries;
    // key = hourKey + "\t" + ip
    private final Map<String, AccessStat> stats = new ConcurrentHashMap<>();

    public AccessStatsRegistry(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /** 累加一次请求。/ record one request. */
    public void record(String hourKey, String ip, AccessBucket bucket, long bytes, long nowMillis) {
        String key = hourKey + "\t" + ip;
        AccessStat s = stats.get(key);
        if (s == null) {
            if (stats.size() < maxEntries) {
                AccessStat created = new AccessStat(hourKey, ip);
                AccessStat raced = stats.putIfAbsent(key, created);
                s = raced != null ? raced : created;
            } else {
                // 超出 IP 上限 → 同小时共用一条 / overflow into one shared entry for the hour
                s = stats.computeIfAbsent(hourKey + "\t" + OVERFLOW_IP, k -> new AccessStat(hourKey, OVERFLOW_IP));
            }
        }
        s.record(bucket, bytes, nowMillis);
    }

    /** 某天各 IP 的汇总(跨小时合并),按请求数倒序。/ per-IP day totals (hours merged), sorted desc. */
    public List<AccessStat> forDate(String dateStr) {
        Map<String, AccessStat> byIp = new HashMap<>();
        for (AccessStat s : stats.values()) {
            if (!startsWithData(s.getHourKey(), dateStr)) {
                continue;
            }
            AccessStat snap = s.copy();
            AccessStat merged = byIp.get(snap.getIp());
            if (merged == null) {
                merged = new AccessStat(dateStr, snap.getIp());
                byIp.put(snap.getIp(), merged);
            }
            merged.add(snap);
        }
        List<AccessStat> list = new ArrayList<>(byIp.values());
        list.sort(Comparator.comparingLong(AccessStat::getRequests).reversed());
        return list;
    }

    /** 某 IP 某天按小时的分布(24 个桶,缺的补零)。/ per-hour breakdown for an IP on a day (24 buckets). */
    public List<AccessStat> hourlyForIp(String dateStr, String ip) {
        AccessStat[] hours = new AccessStat[24];
        for (int h = 0; h < 24; h++) {
            hours[h] = new AccessStat(dateStr + " " + twoDigit(h), ip);
        }
        for (AccessStat s : stats.values()) {
            if (ip.equals(s.getIp()) && startsWithData(s.getHourKey(), dateStr)) {
                int hour = parseHour(s.getHourKey());
                if (hour >= 0 && hour < 24) {
                    hours[hour].add(s.copy());
                }
            }
        }
        return Arrays.asList(hours);
    }

    /** 全量快照(副本),用于持久化。/ full snapshot (copies) for persistence. */
    public List<AccessStat> snapshotData() {
        List<AccessStat> out = new ArrayList<>(stats.size());
        for (AccessStat s : stats.values()) {
            out.add(s.copy());
        }
        return out;
    }

    /** 从持久化数据恢复(启动时)。/ restore from persisted data (on startup). */
    public void restoreData(List<AccessStat> all) {
        stats.clear();
        for (AccessStat s : all) {
            if (s.getHourKey() != null && s.getIp() != null) {
                stats.put(s.getHourKey() + "\t" + s.getIp(), s.copy());
            }
        }
    }

    /** 丢弃日期早于 dateStr 的小时(保留 dateStr 当天及以后)。/ drop hours before the given date. */
    public void trimBefore(String dateStr) {
        stats.entrySet().removeIf(e -> {
            String hk = e.getValue().getHourKey();
            return hk != null && hk.length() >= 10 && hk.substring(0, 10).compareTo(dateStr) < 0;
        });
    }

    private static boolean startsWithData(String hourKey, String dateStr) {
        return hourKey != null && hourKey.length() >= dateStr.length()
                && hourKey.substring(0, dateStr.length()).equals(dateStr);
    }

    private static String twoDigit(int h) {
        return h < 10 ? "0" + h : String.valueOf(h);
    }

    private static int parseHour(String hourKey) {
        // "yyyy-MM-dd HH" → 第 11、12 位是小时 / chars at index 11..12 are the hour
        if (hourKey == null || hourKey.length() < 13) {
            return -1;
        }
        try {
            return Integer.parseInt(hourKey.substring(11, 13));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
