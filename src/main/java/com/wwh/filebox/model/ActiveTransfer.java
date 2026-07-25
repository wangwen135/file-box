package com.wwh.filebox.model;

/**
 * 一个正在进行中的上传/下载(瞬时状态,仅存内存,重启即丢)。
 * An in-flight upload/download (transient state, in-memory only, lost on restart).
 * 用于实时浮窗与关停检查;传输结束后转成 {@link TransferRecord} 落盘。
 * Backs the real-time floating widget and the shutdown check; on completion it is
 * converted to a {@link TransferRecord} and persisted.
 */
public final class ActiveTransfer {

    private final long id;
    private final TransferDirection direction;
    private final String user;          // 用户名(匿名记 anonymous) / username (anonymous for guests)
    private final String space;         // 存储空间名 / storage space name
    private final String file;          // 文件名/相对路径 / filename / relative path
    private final long startedAtMillis; // 开始时间(毫秒) / start time (ms)

    public ActiveTransfer(long id, TransferDirection direction, String user, String space,
                          String file, long startedAtMillis) {
        this.id = id;
        this.direction = direction;
        this.user = user;
        this.space = space;
        this.file = file;
        this.startedAtMillis = startedAtMillis;
    }

    public long getId() { return id; }
    public TransferDirection getDirection() { return direction; }
    public String getUser() { return user; }
    public String getSpace() { return space; }
    public String getFile() { return file; }
    public long getStartedAtMillis() { return startedAtMillis; }
}
