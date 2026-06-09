package com.parkomfy.websocket;

import com.parkomfy.service.ParkingEventBroadcaster;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ParkingWebSocketHandler extends TextWebSocketHandler {

    private final ParkingEventBroadcaster broadcaster;

    public ParkingWebSocketHandler(ParkingEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        broadcaster.register(session);
        try {
            session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"message\":\"PARKOMFY live feed\"}"));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }
}
