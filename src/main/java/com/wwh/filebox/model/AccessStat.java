package com.wwh.filebox.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一个"小时 × IP"维度的访问聚合(请求数、流量、按桶分布、末次时间)。
 * Hour × IP access aggregate (request count, bytes, per-bucket counts, last-seen).
 * record() 与 copy() 加 synchronized,保证高并发下的读写一致。
 */
public final class AccessStat {

    private final String hourKey;   // "yyyy-MM-dd HH"
    private final String ip;
    private long requests;
    private long bytes;
    private long listRequests;
    private long downloadRequests;
    private long otherRequests;
    private long lastSeenMillis;

    public AccessStat(String hourKey, String ip) {
        this.hourKey = hourKey;
        this.ip = ip;
    }

    @JsonCreator
    public AccessStat(
            @JsonProperty("hourKey") String hourKey,
            @JsonProperty("ip") String ip,
            @JsonProperty("requests") long requests,
            @JsonProperty("bytes") long bytes,
            @JsonProperty("listRequests") long listRequests,
            @JsonProperty("downloadRequests") long downloadRequests,
            @JsonProperty("otherRequests") long otherRequests,
            @JsonProperty("lastSeenMillis") long lastSeenMillis) {
        this.hourKey = hourKey;
        this.ip = ip;
        this.requests = requests;
        this.bytes = bytes;
        this.listRequests = listRequests;
        this.downloadRequests = downloadRequests;
        this.otherRequests = otherRequests;
        this.lastSeenMillis = lastSeenMillis;
    }

    /** 累加一次请求:总数+1、流量累加、对应桶+1、刷新末次时间。/ add one request. */
    public synchronized void record(AccessBucket bucket, long bytes, long nowMillis) {
        this.requests++;
        this.bytes += bytes;
        switch (bucket) {
            case LIST:
                listRequests++;
                break;
            case DOWNLOAD:
                downloadRequests++;
                break;
            default:
                otherRequests++;
                break;
        }
        this.lastSeenMillis = nowMillis;
    }

    /** 把另一份(通常是 copy 出来的快照)计数累加到本对象。/ add another stat's counts into this one. */
    public synchronized void add(AccessStat other) {
        this.requests += other.requests;
        this.bytes += other.bytes;
        this.listRequests += other.listRequests;
        this.downloadRequests += other.downloadRequests;
        this.otherRequests += other.otherRequests;
        if (other.lastSeenMillis > this.lastSeenMillis) {
            this.lastSeenMillis = other.lastSeenMillis;
        }
    }

    /** 取一份互不影响的快照副本。/ an independent snapshot copy. */
    public synchronized AccessStat copy() {
        AccessStat c = new AccessStat(hourKey, ip);
        c.requests = requests;
        c.bytes = bytes;
        c.listRequests = listRequests;
        c.downloadRequests = downloadRequests;
        c.otherRequests = otherRequests;
        c.lastSeenMillis = lastSeenMillis;
        return c;
    }

    public String getHourKey() { return hourKey; }
    public String getIp() { return ip; }
    public synchronized long getRequests() { return requests; }
    public synchronized long getBytes() { return bytes; }
    public synchronized long getListRequests() { return listRequests; }
    public synchronized long getDownloadRequests() { return downloadRequests; }
    public synchronized long getOtherRequests() { return otherRequests; }
    public synchronized long getLastSeenMillis() { return lastSeenMillis; }
}
