package com.wwh.filebox.service;

import com.wwh.filebox.model.TransferDirection;
import com.wwh.filebox.model.TransferRecord;
import com.wwh.filebox.model.TransferStats;
import com.wwh.filebox.model.TransferStats.GroupStat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一组传输记录聚合成汇总统计(总字节/次数、平均速度、按用户/空间分组)。
 * Aggregates a list of transfer records into summary stats (total bytes/counts, average speed,
 * grouped by user/space).
 */
public class TransferStatsCalculator {

    public TransferStats compute(List<TransferRecord> records) {
        long upBytes = 0, downBytes = 0;
        int upCount = 0, downCount = 0;
        long totalBytes = 0, totalDurationMs = 0;

        // 分组累加器:[0]=上传字节 [1]=下载字节 [2]=次数 / per-group accumulator: [0]upBytes [1]downBytes [2]count
        Map<String, long[]> byUser = new LinkedHashMap<>();
        Map<String, long[]> bySpace = new LinkedHashMap<>();

        for (TransferRecord r : records) {
            boolean up = r.getDirection() == TransferDirection.UPLOAD;
            long size = r.getSize();
            if (up) {
                upBytes += size;
                upCount++;
            } else {
                downBytes += size;
                downCount++;
            }
            totalBytes += size;
            totalDurationMs += Math.max(0, r.getDurationMillis());
            accumulate(byUser, r.getUser(), up, size);
            accumulate(bySpace, r.getSpace(), up, size);
        }

        long avgBps = 0;
        if (totalDurationMs > 0) {
            // 总字节 / 总秒数 / total bytes divided by total seconds
            avgBps = Math.round(totalBytes / (totalDurationMs / 1000.0));
        }

        return new TransferStats(upBytes, downBytes, upCount, downCount, avgBps,
                toGroups(byUser), toGroups(bySpace));
    }

    private static void accumulate(Map<String, long[]> groups, String key, boolean up, long size) {
        long[] agg = groups.computeIfAbsent(key == null ? "" : key, k -> new long[3]);
        if (up) {
            agg[0] += size;
        } else {
            agg[1] += size;
        }
        agg[2] += 1;
    }

    private static List<GroupStat> toGroups(Map<String, long[]> groups) {
        List<GroupStat> out = new ArrayList<>(groups.size());
        for (Map.Entry<String, long[]> entry : groups.entrySet()) {
            long[] agg = entry.getValue();
            out.add(new GroupStat(entry.getKey(), agg[0], agg[1], (int) agg[2]));
        }
        return out;
    }
}
