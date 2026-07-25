package com.wwh.filebox.model;

import java.util.List;

/**
 * 一组传输记录的汇总统计:上传/下载字节数与次数、平均速度,以及按用户/空间分组。
 * Aggregate stats over a set of transfer records: upload/download bytes & counts, average speed,
 * grouped by user and by space. 用于后台日志页顶部的汇总条。
 */
public final class TransferStats {

    public final long uploadBytes;
    public final long downloadBytes;
    public final int uploadCount;
    public final int downloadCount;
    /** 平均吞吐(字节/秒)= 总字节数 / 总耗时秒数;无耗时时为 0 / avg throughput (B/s) = total bytes / total seconds; 0 if no duration. */
    public final long avgSpeedBytesPerSecond;
    public final List<GroupStat> byUser;
    public final List<GroupStat> bySpace;

    public TransferStats(long uploadBytes, long downloadBytes, int uploadCount, int downloadCount,
                         long avgSpeedBytesPerSecond, List<GroupStat> byUser, List<GroupStat> bySpace) {
        this.uploadBytes = uploadBytes;
        this.downloadBytes = downloadBytes;
        this.uploadCount = uploadCount;
        this.downloadCount = downloadCount;
        this.avgSpeedBytesPerSecond = avgSpeedBytesPerSecond;
        this.byUser = byUser;
        this.bySpace = bySpace;
    }

    /** 单个分组(按用户或按空间)的汇总 / aggregate for one group (by user or by space). */
    public static final class GroupStat {
        public final String name;
        public final long uploadBytes;
        public final long downloadBytes;
        public final int count;

        public GroupStat(String name, long uploadBytes, long downloadBytes, int count) {
            this.name = name;
            this.uploadBytes = uploadBytes;
            this.downloadBytes = downloadBytes;
            this.count = count;
        }
    }
}
