package com.wwh.filebox.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一条已完成的上传/下载记录,序列化为日志文件里的一行 JSON。
 * A completed upload/download entry, serialized as one JSONL line in the log file.
 */
public final class TransferRecord {

    private final long timeMillis;          // 完成时间(毫秒) / completion time (ms)
    private final TransferDirection direction;
    private final String user;              // 用户名(匿名记 anonymous) / username (anonymous for guests)
    private final String space;             // 存储空间名 / storage space name
    private final String file;              // 存储相对路径/文件名 / storage-relative path/filename
    private final long size;                // 字节数 / bytes
    private final TransferResult result;    // 成功/失败 / success or failure
    private final String ip;                // 客户端 IP / client IP
    private final long durationMillis;      // 耗时(毫秒) / duration in ms

    @JsonCreator
    public TransferRecord(
            @JsonProperty("timeMillis") long timeMillis,
            @JsonProperty("direction") TransferDirection direction,
            @JsonProperty("user") String user,
            @JsonProperty("space") String space,
            @JsonProperty("file") String file,
            @JsonProperty("size") long size,
            @JsonProperty("result") TransferResult result,
            @JsonProperty("ip") String ip,
            @JsonProperty("durationMillis") long durationMillis) {
        this.timeMillis = timeMillis;
        this.direction = direction;
        this.user = user;
        this.space = space;
        this.file = file;
        this.size = size;
        this.result = result;
        this.ip = ip;
        this.durationMillis = durationMillis;
    }

    public long getTimeMillis() { return timeMillis; }
    public TransferDirection getDirection() { return direction; }
    public String getUser() { return user; }
    public String getSpace() { return space; }
    public String getFile() { return file; }
    public long getSize() { return size; }
    public TransferResult getResult() { return result; }
    public String getIp() { return ip; }
    public long getDurationMillis() { return durationMillis; }
}
