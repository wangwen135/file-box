package com.wwh.filebox.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 /ws/transfers 端点,握手需管理员 token(见 TransferHandshakeInterceptor)。
 * Registers the /ws/transfers endpoint; the handshake requires an admin token (see
 * TransferHandshakeInterceptor).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private TransferWebSocketHandler handler;

    @Autowired
    private TransferHandshakeInterceptor interceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/transfers")
                .addInterceptors(interceptor)
                .setAllowedOrigins("*");
    }
}
