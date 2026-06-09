package com.parkomfy.service;

import com.parkomfy.model.SlotReservation;
import com.parkomfy.repository.IParkingRepository;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Expo push notifications + reservation reminders.
 */
public class NotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final IParkingRepository repository;
    private final ParkingEventBroadcaster broadcaster;

    public NotificationService(IParkingRepository repository, ParkingEventBroadcaster broadcaster) {
        this.repository = repository;
        this.broadcaster = broadcaster;
    }

    public void registerPushToken(String userId, String expoPushToken) {
        if (userId == null || expoPushToken == null || expoPushToken.isBlank()) return;
        repository.savePushToken(userId, expoPushToken.trim());
    }

    public void sendToUser(String userId, String title, String body) {
        List<String> tokens = repository.getPushTokensForUser(userId);
        for (String token : tokens) {
            sendExpoPush(token, title, body);
        }
    }

    public void sendToPlate(String licensePlate, String title, String body) {
        if (licensePlate == null) return;
        String normalized = licensePlate.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        List<String> tokens = repository.getPushTokensForPlate(normalized);
        for (String token : tokens) {
            sendExpoPush(token, title, body);
        }
    }

    public void notifyWrongSlot(String plate, String expectedSlot, String actualSlot, String areaId) {
        String title = "Yanlış slot uyarısı";
        String body = plate + " aracı " + actualSlot + " slotunda; rezervasyon: " + expectedSlot;
        sendToPlate(plate, title, body);
        broadcaster.broadcastNotification(title, body, areaId);
    }

    public void notifyReservationReminder(SlotReservation reservation) {
        String title = "Rezervasyon hatırlatması";
        String body = reservation.getLicensePlate() + " için " + reservation.getSlotId()
            + " slotu " + reservation.getStartTime() + " saatinde başlıyor.";
        sendToPlate(reservation.getLicensePlate(), title, body);
    }

    public void notifyVehicleParked(String plate, String slotId) {
        String title = "Araç park edildi";
        String body = plate + " aracı " + slotId + " slotuna yerleştirildi.";
        sendToPlate(plate, title, body);
    }

    /** Check reservations starting in ~15 minutes and send reminders. */
    public void processReservationReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusMinutes(16);
        for (SlotReservation r : repository.getUpcomingReservations(now, windowEnd)) {
            long mins = ChronoUnit.MINUTES.between(now, r.getStartTime());
            if (mins >= 14 && mins <= 16) {
                notifyReservationReminder(r);
            }
        }
    }

    private void sendExpoPush(String expoPushToken, String title, String body) {
        try {
            String json = "{\"to\":\"" + expoPushToken + "\",\"title\":\""
                + escapeJson(title) + "\",\"body\":\"" + escapeJson(body) + "\",\"sound\":\"default\"}";
            URL url = new URL(EXPO_PUSH_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("Expo push failed: " + e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
