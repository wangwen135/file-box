package com.wwh.filebox.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwh.filebox.model.ActiveTransfer;
import com.wwh.filebox.service.ActiveTransferRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 推送"当前活跃传输"快照给已连接的管理员前端。注册表一变(传输开始/结束)即广播整份快照。
 * Pushes the active-transfer snapshot to connected admin frontends. Broadcasts the whole snapshot
 * whenever the registry changes (a transfer starts/ends).
 */
@Component
public class TransferWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TransferWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ActiveTransferRegistry registry;

    // sendMessage 同一 session 非线程安全,广播时按 session 加锁 / not thread-safe per session; lock per session
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @PostConstruct
    void registerListener() {
        // 注册表一变就广播 / broadcast whenever the active set changes
        registry.addChangeListener(this::broadcast);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        sessions.add(session);
        sendSnapshot(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /** 把最新快照推给所有连接。/ push the latest snapshot to every open connection. */
    void broadcast() {
        String json = snapshotJson();
        if (json == null) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            synchronized (session) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    logger.debug("WS push failed: {}", e.getMessage());
                }
            }
        }
    }

    private void sendSnapshot(WebSocketSession session) throws IOException {
        String json = snapshotJson();
        if (json == null) {
            return;
        }
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }

    private String snapshotJson() {
        List<ActiveTransfer> snapshot = registry.snapshot();
        Map<String, Object> payload = new HashMap<>();
        payload.put("transfers", snapshot);
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (IOException e) {
            logger.warn("Failed to serialize transfer snapshot: {}", e.getMessage());
            return null;
        }
    }
}
