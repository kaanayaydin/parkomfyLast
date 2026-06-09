package com.parkomfy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxies frames from Python camera simulator (loop1/2/3.mp4) on localhost:50052.
 */
public class CameraSimulationService {

    private static final String CAMERA_BASE = "http://127.0.0.1:50052";
    private final ObjectMapper mapper = new ObjectMapper();

    public byte[] getLiveSnapshot() {
        return getLiveSnapshotForLot(null);
    }

    /** lotKey: loop1, loop2, loop3 — her otopark kendi paralel stream'inden. */
    public byte[] getLiveSnapshotForLot(String lotKey) {
        try {
            String lot = normalizeLotKey(lotKey);
            URL url = new URL(CAMERA_BASE + "/snapshot/" + lot + ".jpg?t=" + System.currentTimeMillis());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            System.err.println("Live camera snapshot failed: " + e.getMessage());
            return null;
        }
    }

    private static String normalizeLotKey(String lotKey) {
        if (lotKey == null || lotKey.isBlank()) {
            return "loop1";
        }
        String k = lotKey.trim().toLowerCase().replace(".mp4", "");
        if (k.startsWith("istasyon")) {
            String n = k.replace("istasyon", "");
            if (!n.isBlank()) {
                return "loop" + n;
            }
        }
        return k.startsWith("loop") ? k : "loop" + k;
    }

    public boolean isCameraAvailable() {
        try {
            URL url = new URL(CAMERA_BASE + "/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public String getCurrentVideoSource() {
        try {
            URL url = new URL(CAMERA_BASE + "/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            if (conn.getResponseCode() != 200) {
                return "loop1.mp4";
            }
            JsonNode node = mapper.readTree(conn.getInputStream());
            return node.path("video").asText("loop1.mp4");
        } catch (Exception e) {
            return "loop1.mp4";
        }
    }

    public List<Map<String, String>> listAvailableVideos() {
        List<Map<String, String>> fallback = new ArrayList<>();
        for (String id : new String[]{"loop1", "loop2", "loop3"}) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", id + ".mp4");
            m.put("label", "Loop " + id.replace("loop", ""));
            fallback.add(m);
        }
        try {
            URL url = new URL(CAMERA_BASE + "/videos");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            if (conn.getResponseCode() != 200) {
                return fallback;
            }
            JsonNode root = mapper.readTree(conn.getInputStream());
            List<Map<String, String>> out = new ArrayList<>();
            for (JsonNode v : root.path("videos")) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("id", v.path("id").asText());
                m.put("name", v.path("name").asText());
                m.put("label", v.path("label").asText());
                out.add(m);
            }
            return out.isEmpty() ? fallback : out;
        } catch (Exception e) {
            return fallback;
        }
    }

    public Map<String, Object> switchVideo(String videoId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            URL url = new URL(CAMERA_BASE + "/switch");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            String body = mapper.writeValueAsString(Map.of("video", videoId));
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            JsonNode node = mapper.readTree(in);
            result.put("success", node.path("success").asBoolean(false));
            result.put("message", node.path("message").asText(""));
            result.put("current", node.path("current").asText(videoId + ".mp4"));
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            result.put("current", getCurrentVideoSource());
            return result;
        }
    }

    /** MJPEG akışını Python'dan istemciye kopyala. */
    public void proxyMjpegStream(OutputStream clientOut) throws Exception {
        URL url = new URL(CAMERA_BASE + "/live.mjpg");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(0);
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() != 200) {
            throw new IllegalStateException("MJPEG stream unavailable: HTTP " + conn.getResponseCode());
        }
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                clientOut.write(buf, 0, n);
                clientOut.flush();
            }
        }
    }
}
