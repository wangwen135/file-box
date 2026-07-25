package com.wwh.filebox.service;

import com.wwh.filebox.model.ActiveTransfer;
import com.wwh.filebox.model.TransferDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程安全的"活跃传输"登记表:传输开始时登记、结束时注销,可取当前快照(供 WS 推送与关停检查)。
 * start/end 会通知变更监听器(WS 层订阅,用于实时推送当前活跃列表)。
 * Thread-safe registry of in-flight transfers: register on start, deregister on end, snapshot the
 * current set (for WS push and the shutdown check). start/end notify change listeners (the WS layer
 * subscribes to push the live active list).
 */
public class ActiveTransferRegistry {

    // 仅需进程内唯一(会话随重启丢失),用自增计数即可 / only needs to be unique per process
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, ActiveTransfer> active = new ConcurrentHashMap<>();
    // 变更监听器:WS 层用来在活跃列表变化时推送快照 / change listeners for WS push on change
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    /**
     * 登记一个新传输,返回用于注销的句柄 id。
     * Register a new in-flight transfer; returns the handle id used to end it.
     */
    public long start(TransferDirection direction, String user, String space, String file) {
        long id = nextId.getAndIncrement();
        active.put(id, new ActiveTransfer(id, direction, user, space, file, System.currentTimeMillis()));
        notifyChangeListeners();
        return id;
    }

    /** 注销一个传输(传输结束,无论成功失败)。/ Deregister a transfer (completed or failed). */
    public void end(long id) {
        active.remove(id);
        notifyChangeListeners();
    }

    /** 当前活跃传输的快照(副本,可安全遍历/序列化)。/ A snapshot copy of currently active transfers. */
    public List<ActiveTransfer> snapshot() {
        return new ArrayList<>(active.values());
    }

    /** 是否没有活跃传输(关停检查用)。/ Whether no transfers are in flight (for the shutdown check). */
    public boolean isEmpty() {
        return active.isEmpty();
    }

    /**
     * 订阅活跃列表的变化(开始/结束都会触发)。监听器抛出的异常会被吞掉,不影响登记表本身。
     * Subscribe to active-list changes (fired on start/end). Listener exceptions are swallowed so
     * they can't break the registry.
     */
    public void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    private void notifyChangeListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                // 监听器异常不影响登记表 / a listener failure must not affect the registry
            }
        }
    }
}
