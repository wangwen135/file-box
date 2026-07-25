package com.wwh.filebox.service;

import com.wwh.filebox.model.TransferDirection;
import com.wwh.filebox.model.TransferRecord;
import com.wwh.filebox.model.TransferResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 把"登记活跃→计时→注销→写日志"封进一个句柄,供上传/下载端点在 finally 里调用。
 * complete/fail 永不抛异常:日志写失败只记 warn,不影响真实传输(登记表仍会注销)。
 * Wraps register→time→deregister→log into a handle for upload/download endpoints to call in finally.
 * complete/fail never throw: a log-write failure is only warned and never breaks the real transfer
 * (the registry is still deregistered).
 */
public class TransferRecorder {

    private static final Logger logger = LoggerFactory.getLogger(TransferRecorder.class);

    private final ActiveTransferRegistry registry;
    private final JsonlLog transferLog;

    public TransferRecorder(ActiveTransferRegistry registry, JsonlLog transferLog) {
        this.registry = registry;
        this.transferLog = transferLog;
    }

    /**
     * 开始记录一次传输,返回句柄;进行中会出现在活跃表(浮窗/WS 可见)。
     * Begin recording a transfer; while in flight it appears in the active registry (widget/WS).
     */
    public Handle begin(TransferDirection direction, String user, String space, String file, String ip) {
        long id = registry.start(direction, user, space, file);
        return new Handle(this, direction, user, space, file, ip, id, System.currentTimeMillis());
    }

    private void finish(Handle h, long bytes, TransferResult result) {
        long now = System.currentTimeMillis();
        registry.end(h.id);
        try {
            transferLog.append(new TransferRecord(now, h.direction, h.user, h.space, h.file,
                    bytes, result, h.ip, now - h.startedAtMillis));
        } catch (IOException e) {
            // 日志写失败不影响真实传输结果 / a log-write failure must not affect the real transfer
            logger.warn("Failed to append transfer record ({} {}): {}", h.direction, h.file, e.getMessage());
        }
    }

    /** 一次传输的记录句柄:complete(bytes)= 成功落库;fail()= 失败(0 字节)落库。/ handle: complete or fail. */
    public static final class Handle {
        private final TransferRecorder recorder;
        private final TransferDirection direction;
        private final String user;
        private final String space;
        private final String file;
        private final String ip;
        private final long id;
        private final long startedAtMillis;

        Handle(TransferRecorder recorder, TransferDirection direction, String user, String space,
               String file, String ip, long id, long startedAtMillis) {
            this.recorder = recorder;
            this.direction = direction;
            this.user = user;
            this.space = space;
            this.file = file;
            this.ip = ip;
            this.id = id;
            this.startedAtMillis = startedAtMillis;
        }

        /** 成功完成,记录实际字节数 / completed successfully, logging the byte count. */
        public void complete(long bytes) {
            recorder.finish(this, bytes, TransferResult.SUCCESS);
        }

        /** 失败或被拒,记 0 字节 / failed or rejected, logging zero bytes. */
        public void fail() {
            recorder.finish(this, 0L, TransferResult.FAILED);
        }
    }
}
