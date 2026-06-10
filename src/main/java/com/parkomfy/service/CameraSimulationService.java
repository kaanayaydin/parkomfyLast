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
 * Proxies frames from Python camera simulator (yen1..yen5.mp4) on localhost:50052.
 */
public class CameraSimulationService {

    private static final String CAMERA_BASE = "http://127.0.0.1:50052";
    public static final String PRIMARY_GATE_LOT = "yen1";
    private final ObjectMapper mapper = new ObjectMapper();

    public byte[] getLiveSnapshot() {
        CameraFrame frame = fetchLiveSnapshot(null);
        return frame != null ? frame.jpeg : null;
    }

    /** lotKey: yen1 .. yen5 — her otopark kendi paralel stream'inden. */
    public byte[] getLiveSnapshotForLot(String lotKey) {
        CameraFrame frame = fetchLiveSnapshot(lotKey);
        return frame != null ? frame.jpeg : null;
    }

    public CameraFrame fetchLiveSnapshot(String lotKey) {
        try {
            String lot = normalizeLotKey(lotKey);
            URL url = new URL(CAMERA_BASE + "/snapshot/" + lot + ".jpg?t=" + System.currentTimeMillis());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            byte[] jpeg;
            try (InputStream in = conn.getInputStream()) {
                jpeg = in.readAllBytes();
            }
            String lotHeader = conn.getHeaderField("X-Parkomfy-Lot");
            double positionSec = parseHeaderDouble(conn.getHeaderField("X-Parkomfy-Position-Sec"));
            long loopIndex = parseHeaderLong(conn.getHeaderField("X-Parkomfy-Loop-Index"));
            return new CameraFrame(jpeg, lotHeader != null ? lotHeader : lot, positionSec, loopIndex);
        } catch (Exception e) {
            System.err.println("Live camera snapshot failed: " + e.getMessage());
            return null;
        }
    }

    private static double parseHeaderDouble(String v) {
        if (v == null || v.isBlank()) return 0.0;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static long parseHeaderLong(String v) {
        if (v == null || v.isBlank()) return 0L;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static final class CameraFrame {
        public final byte[] jpeg;
        public final String lot;
        public final double positionSec;
        public final long loopIndex;

        public CameraFrame(byte[] jpeg, String lot, double positionSec, long loopIndex) {
            this.jpeg = jpeg;
            this.lot = lot;
            this.positionSec = positionSec;
            this.loopIndex = loopIndex;
        }
    }

    private static String normalizeLotKey(String lotKey) {
        if (lotKey == null || lotKey.isBlank()) {
            return PRIMARY_GATE_LOT;
        }
        String k = lotKey.trim().toLowerCase().replace(".mp4", "");
        if (k.equals("giris") || k.equals("entry")) {
            return "giris";
        }
        if (k.equals("cikis") || k.equals("exit")) {
            return "cikis";
        }
        if (k.startsWith("yen")) {
            return k;
        }
        if (k.startsWith("loop") && k.length() > 4 && Character.isDigit(k.charAt(4))) {
            return "yen" + k.substring(4);
        }
        if (k.startsWith("istasyon")) {
            String n = k.replace("istasyon", "");
            if (!n.isBlank()) {
                return "yen" + n;
            }
        }
        return k.startsWith("yen") ? k : PRIMARY_GATE_LOT;
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
                return "yen1.mp4";
            }
            JsonNode node = mapper.readTree(conn.getInputStream());
            return node.path("video").asText("yen1.mp4");
        } catch (Exception e) {
            return "yen1.mp4";
        }
    }

    public List<Map<String, String>> listAvailableVideos() {
        List<Map<String, String>> fallback = new ArrayList<>();
        for (String id : new String[]{"yen1", "yen2", "yen3", "yen4", "yen5"}) {
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

    /** Giriş/çıkış plaka kamerası oynuyor mu? */
    public boolean isGatePlaying(String gate) {
        try {
            JsonNode node = fetchGateStatus();
            if (node == null) {
                return false;
            }
            String key = gate != null && gate.toLowerCase().contains("cik") ? "cikis" : "giris";
            return node.path(key).path("playing").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode fetchGateStatus() {
        try {
            URL url = new URL(CAMERA_BASE + "/gate/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            return mapper.readTree(conn.getInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    /** loop1 video oynatma konumu (giriş kamerası zaman senkronu). */
    public Map<String, Object> getLotTimeline(String lotKey) {
        Map<String, Object> empty = new LinkedHashMap<>();
        try {
            String lot = normalizeLotKey(lotKey);
            URL url = new URL(CAMERA_BASE + "/timeline/" + lot);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                return empty;
            }
            JsonNode node = mapper.readTree(conn.getInputStream());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("positionSec", node.path("positionSec").asDouble(0));
            out.put("durationSec", node.path("durationSec").asDouble(0));
            out.put("loopIndex", node.path("loopIndex").asLong(0));
            out.put("girisTriggerSec", node.path("girisTriggerSec").asDouble(13.5));
            return out;
        } catch (Exception e) {
            return empty;
        }
    }

    /** loop1 doluluk değişince plakaokuma.mp4 oynat (giris / cikis). */
    public boolean triggerGate(String gate) {
        try {
            URL url = new URL(CAMERA_BASE + "/gate/trigger");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            String body = mapper.writeValueAsString(Map.of("gate", gate));
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() != 200) {
                return false;
            }
            JsonNode node = mapper.readTree(conn.getInputStream());
            return node.path("success").asBoolean(false);
        } catch (Exception e) {
            System.err.println("Gate trigger failed: " + e.getMessage());
            return false;
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
