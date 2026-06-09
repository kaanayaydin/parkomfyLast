package com.parkomfy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parkomfy.api.LiveParkingStatusDto;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Broadcasts parking status updates to WebSocket clients.
 */
public class ParkingEventBroadcaster {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String, byte[]> annotatedImageCache = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void cacheAnnotatedImage(String areaId, byte[] jpeg) {
        if (areaId != null && jpeg != null && jpeg.length > 0) {
            annotatedImageCache.put(areaId, jpeg);
        }
    }

    public byte[] getCachedImage(String areaId) {
        return annotatedImageCache.get(areaId);
    }

    public void broadcastLiveStatus(LiveParkingStatusDto status) {
        if (status == null) return;
        try {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("type", "LIVE_STATUS");
            payload.put("data", status);
            String json = mapper.writeValueAsString(payload);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    s.sendMessage(msg);
                }
            }
        } catch (Exception e) {
            System.err.println("WebSocket broadcast failed: " + e.getMessage());
        }
    }

    public void broadcastNotification(String title, String body, String areaId) {
        try {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("type", "NOTIFICATION");
            payload.put("title", title);
            payload.put("body", body);
            payload.put("areaId", areaId);
            String json = mapper.writeValueAsString(payload);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    s.sendMessage(msg);
                }
            }
        } catch (Exception e) {
            System.err.println("WebSocket notification broadcast failed: " + e.getMessage());
        }
    }
}
