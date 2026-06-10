package com.parkomfy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;
import com.parkomfy.api.*;
import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.model.ParkingSlotResultDto;
import com.parkomfy.model.Camera;
import com.parkomfy.model.ParkingSlot;
import com.parkomfy.service.AuthService;
import com.parkomfy.service.CameraSimulationService;
import com.parkomfy.service.IDetectionService;
import com.parkomfy.service.ParkingEventBroadcaster;
import com.parkomfy.repository.IParkingRepository;

import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Parking Operations
 * Exposes endpoints for mobile clients
 * 
 * Base URL: /api/v1
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ParkingRestController {
    
    @Autowired
    private ParkingApiController apiController;

    @Autowired
    private IDetectionService detectionService;

    @Autowired
    private IYOLOInference yoloInference;

    @Autowired
    private AuthService authService;

    @Autowired
    private ParkingEventBroadcaster eventBroadcaster;

    @Autowired
    private CameraSimulationService cameraSimulationService;

    @Autowired
    private IParkingRepository parkingRepository;
    
    // ============================================
    // AUTH ENDPOINTS
    // ============================================

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<UserDto>> register(@RequestBody RegisterRequest request) {
        try {
            UserDto user = authService.register(request);
            return ResponseEntity.status(201).body(ApiResponse.success(user, "Kayıt başarılı", 201));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), 400));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(ApiResponse.error(e.getMessage(), 409));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Kayıt hatası: " + e.getMessage(), 500));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<UserDto>> login(@RequestBody LoginRequest request) {
        try {
            UserDto user = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success(user, "Giriş başarılı"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(ApiResponse.error(e.getMessage(), 401));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Giriş hatası: " + e.getMessage(), 500));
        }
    }

    // ============================================
    // PARKING AREA ENDPOINTS
    // ============================================
    
    /**
     * GET /api/v1/parking/status
     * Get real-time parking area status
     */
    @GetMapping("/parking/status")
    public ResponseEntity<ApiResponse<ParkingStatusDto>> getParkingStatus(
            @RequestParam String areaId) {
        ApiResponse<ParkingStatusDto> response = apiController.getParkingStatus(areaId);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }

    @GetMapping("/admin/live-hybrid")
    public ResponseEntity<ApiResponse<LiveParkingStatusDto>> getLiveHybridStatus(
            @RequestParam String areaId) {
        ApiResponse<LiveParkingStatusDto> response = apiController.getLiveHybridStatus(areaId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/parking/live")
    public ResponseEntity<ApiResponse<LiveParkingStatusDto>> getLiveParkingStatus(
            @RequestParam String areaId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        ApiResponse<LiveParkingStatusDto> response = apiController.getLiveParkingStatus(areaId, startTime, endTime);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/parking/reservation-view")
    public ResponseEntity<ApiResponse<AreaReservationViewDto>> getReservationView(
            @RequestParam String areaId,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String timelineStart,
            @RequestParam(required = false) String timelineEnd) {
        ApiResponse<AreaReservationViewDto> response = apiController.getReservationView(
            areaId, startTime, endTime, timelineStart, timelineEnd);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping(value = "/parking/live-image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getLiveParkingImage(@RequestParam String areaId) {
        byte[] cached = eventBroadcaster.getCachedImage(areaId);
        if (cached != null && cached.length > 0) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(cached);
        }
        return ResponseEntity.notFound().build();
    }

    // ============================================
    // LIVE CAMERA SIMULATION (loop1.mp4)
    // ============================================

    @GetMapping("/camera/live/status")
    public ResponseEntity<Map<String, Object>> getLiveCameraStatus() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("available", cameraSimulationService.isCameraAvailable());
        body.put("source", cameraSimulationService.getCurrentVideoSource());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/camera/live/videos")
    public ResponseEntity<Map<String, Object>> listCameraVideos() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("videos", cameraSimulationService.listAvailableVideos());
        body.put("current", cameraSimulationService.getCurrentVideoSource());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/camera/live/video")
    public ResponseEntity<Map<String, Object>> switchCameraVideo(@RequestParam String video) {
        Map<String, Object> result = cameraSimulationService.switchVideo(video);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ResponseEntity.status(ok ? 200 : 400).body(result);
    }

    @GetMapping(value = "/camera/live/stream", produces = "multipart/x-mixed-replace; boundary=frame")
    public ResponseEntity<StreamingResponseBody> getLiveCameraStream() {
        if (!cameraSimulationService.isCameraAvailable()) {
            return ResponseEntity.status(503).build();
        }
        StreamingResponseBody stream = out -> {
            try {
                cameraSimulationService.proxyMjpegStream(out);
            } catch (Exception e) {
                System.err.println("MJPEG proxy ended: " + e.getMessage());
            }
        };
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store, no-cache")
            .header("Connection", "close")
            .body(stream);
    }

    @GetMapping(value = "/camera/live/snapshot", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getLiveCameraSnapshot(
            @RequestParam(required = false) String lot) {
        byte[] frame = cameraSimulationService.getLiveSnapshotForLot(lot);
        if (frame == null || frame.length == 0) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header("Cache-Control", "no-store")
            .body(frame);
    }

    @PostMapping("/camera/live/predict-slots")
    public ResponseEntity<ApiResponse<SlotPredictionResultDto>> predictSlotsFromLiveCamera(
            @RequestParam(required = false) String lot) {
        byte[] frame = cameraSimulationService.getLiveSnapshotForLot(lot);
        if (frame == null || frame.length == 0) {
            return ResponseEntity.status(503)
                .body(ApiResponse.error("Canlı kamera karesi alınamadı. server.py çalışıyor mu?", 503));
        }
        ApiResponse<SlotPredictionResultDto> response = apiController.predictSlotLayout(frame);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/camera/live/scan")
    public ResponseEntity<ApiResponse<ParkingScanResultDto>> scanLiveCamera(@RequestParam String areaId) {
        byte[] frame = cameraSimulationService.getLiveSnapshotForLot(parkingRepository.getLotKey(areaId));
        if (frame == null || frame.length == 0) {
            return ResponseEntity.status(503)
                .body(ApiResponse.error("Canlı kamera karesi alınamadı", 503));
        }
        ApiResponse<ParkingScanResultDto> response = apiController.processParkingCameraScan(frame, areaId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    
    // ============================================
    // PARKING SESSION ENDPOINTS
    // ============================================
    
    /**
     * POST /api/v1/parking/sessions
     * Create new parking session (vehicle entry)
     */
    @PostMapping("/parking/sessions")
    public ResponseEntity<ApiResponse<ParkingSessionDto>> createParkingSession(
            @RequestBody CreateSessionRequest request) {
        ApiResponse<ParkingSessionDto> response = apiController.createParkingSession(request);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }
    
    /**
     * GET /api/v1/parking/sessions/{sessionId}
     * Get parking session details
     */
    @GetMapping("/parking/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<ParkingSessionDto>> getSession(
            @PathVariable String sessionId) {
        ApiResponse<ParkingSessionDto> response = apiController.getSession(sessionId);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }
    
    /**
     * POST /api/v1/parking/sessions/{sessionId}/exit
     * Complete parking session (vehicle exit)
     */
    @PostMapping("/parking/sessions/{sessionId}/exit")
    public ResponseEntity<ApiResponse<PaymentDto>> exitParking(
            @PathVariable String sessionId) {
        ApiResponse<PaymentDto> response = apiController.exitParking(sessionId);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }
    
    // ============================================
    // AVAILABLE SLOTS ENDPOINT
    // ============================================
    
    /**
     * GET /api/v1/parking/slots/available
     * Get list of available parking slots
     */
    @GetMapping("/parking/slots/available")
    public ResponseEntity<ApiResponse<List<ParkingSlotDto>>> getAvailableSlots(
            @RequestParam String areaId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        ApiResponse<List<ParkingSlotDto>> response = apiController.getAvailableSlots(areaId, startTime, endTime);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }

    /**
     * POST /api/v1/parking/reservations
     */
    @PostMapping("/parking/reservations")
    public ResponseEntity<ApiResponse<ReservationDto>> createReservation(
            @RequestBody CreateReservationRequest request) {
        ApiResponse<ReservationDto> response = apiController.createReservation(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * GET /api/v1/parking/reservations?licensePlate=...
     */
    @GetMapping("/parking/reservations")
    public ResponseEntity<ApiResponse<List<ReservationDto>>> getReservations(
            @RequestParam String licensePlate) {
        ApiResponse<List<ReservationDto>> response = apiController.getReservations(licensePlate);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * POST /api/v1/parking/reservations/{reservationId}/cancel?licensePlate=...
     */
    @PostMapping("/parking/reservations/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<ReservationDto>> cancelReservation(
            @PathVariable String reservationId,
            @RequestParam String licensePlate) {
        ApiResponse<ReservationDto> response = apiController.cancelReservation(reservationId, licensePlate);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    
    // ============================================
    // ACTIVE SESSIONS ENDPOINT
    // ============================================
    
    /**
     * GET /api/v1/parking/sessions/active
     * Get all active parking sessions
     */
    @GetMapping("/parking/sessions/active")
    public ResponseEntity<ApiResponse<List<ParkingSessionDto>>> getActiveSessions() {
        ApiResponse<List<ParkingSessionDto>> response = apiController.getActiveSessions();
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }
    
    // ============================================
    // PAYMENT ENDPOINTS
    // ============================================
    
    /**
     * POST /api/v1/payments
     * Process payment for parking session
     */
    @PostMapping("/payments")
    public ResponseEntity<ApiResponse<PaymentResultDto>> processPayment(
            @RequestBody PaymentRequest request) {
        ApiResponse<PaymentResultDto> response = apiController.processPayment(request);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }
    
    /**
     * GET /api/v1/payments/{paymentId}
     * Get payment details
     */
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDto>> getPayment(
            @PathVariable String paymentId) {
        ApiResponse<PaymentDto> response = apiController.getPayment(paymentId);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }
    
    // ============================================
    // HEALTH CHECK ENDPOINT
    // ============================================
    
    /**
     * GET /api/v1/health
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new java.util.HashMap<>();
        response.put("status", "UP");
        response.put("service", "PARKOMFY");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    // ============================================
    // DETECTION TEST (upload parking camera image)
    // ============================================

    /**
     * POST /api/v1/detect/vehicle
     * Upload a parking/camera image (JPEG/PNG) to test vehicle detection.
     * Form field name: "image"
     */
    @PostMapping("/detect/vehicle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detectVehicle(
            @RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Please upload an image file (field name: image)", 400));
        }
        try {
            byte[] bytes = image.getBytes();
            ApiResponse<Map<String, Object>> response = apiController.detectVehicleFromImage(bytes);
            return ResponseEntity.status(response.getStatusCode()).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to read image: " + e.getMessage(), 500));
        }
    }

    /**
     * POST /api/v1/detect/plate
     * Upload an image (e.g. entrance camera) to test license plate detection.
     * Form field name: "image"
     */
    @PostMapping("/entry/plate")
    public ResponseEntity<ApiResponse<EntrancePlateResultDto>> processEntrancePlate(
            @RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("image required", 400));
        }
        try {
            ApiResponse<EntrancePlateResultDto> response = apiController.processEntrancePlate(image.getBytes());
            return ResponseEntity.status(response.getStatusCode()).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage(), 500));
        }
    }

    @PostMapping("/parking/camera/scan")
    public ResponseEntity<ApiResponse<ParkingScanResultDto>> processParkingCameraScan(
            @RequestParam("image") MultipartFile image,
            @RequestParam String areaId) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("image required", 400));
        }
        try {
            ApiResponse<ParkingScanResultDto> response =
                apiController.processParkingCameraScan(image.getBytes(), areaId);
            return ResponseEntity.status(response.getStatusCode()).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage(), 500));
        }
    }

    @PostMapping("/notifications/register")
    public ResponseEntity<ApiResponse<String>> registerPushToken(
            @RequestBody RegisterPushTokenRequest request) {
        ApiResponse<String> response = apiController.registerPushToken(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/admin/reservations")
    public ResponseEntity<ApiResponse<List<ReservationDto>>> getAdminReservations(
            @RequestParam(required = false) String areaId) {
        ApiResponse<List<ReservationDto>> response = apiController.getAdminReservations(areaId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/admin/sessions")
    public ResponseEntity<ApiResponse<List<ParkingSessionDto>>> getAdminSessions(
            @RequestParam String areaId) {
        ApiResponse<List<ParkingSessionDto>> response = apiController.getAdminSessions(areaId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/admin/detections")
    public ResponseEntity<ApiResponse<List<DetectionLogDto>>> getAdminDetections(
            @RequestParam String areaId) {
        ApiResponse<List<DetectionLogDto>> response = apiController.getAdminDetections(areaId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/parking/areas")
    public ResponseEntity<ApiResponse<List<ParkingAreaDto>>> listPublicParkingAreas() {
        ApiResponse<List<ParkingAreaDto>> response = apiController.listPublicParkingAreas();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/admin/parking-areas")
    public ResponseEntity<ApiResponse<List<ParkingAreaDto>>> listParkingAreas() {
        ApiResponse<List<ParkingAreaDto>> response = apiController.listParkingAreas();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/admin/parking-areas")
    public ResponseEntity<ApiResponse<ParkingAreaDto>> createParkingArea(
            @RequestBody CreateParkingAreaRequest request) {
        ApiResponse<ParkingAreaDto> response = apiController.createParkingArea(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/admin/parking-areas/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetParkingData() {
        ApiResponse<Map<String, Object>> response = apiController.resetParkingData();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/admin/parking-areas/predict-slots")
    public ResponseEntity<ApiResponse<SlotPredictionResultDto>> predictSlotLayout(
            @RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("image required", 400));
        }
        try {
            ApiResponse<SlotPredictionResultDto> response =
                apiController.predictSlotLayout(image.getBytes());
            return ResponseEntity.status(response.getStatusCode()).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage(), 500));
        }
    }

    @PostMapping("/admin/parking-areas/calibrate")
    public ResponseEntity<ApiResponse<ParkingAreaDto>> saveSlotCalibration(
            @RequestBody SaveCalibrationRequest request) {
        ApiResponse<ParkingAreaDto> response = apiController.saveSlotCalibration(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/admin/entry-plates")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEntryPlateStatus(
            @RequestParam(required = false) String areaId) {
        ApiResponse<Map<String, Object>> response = apiController.getEntryPlateStatus(areaId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/admin/manual-plate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignManualPlate(
            @RequestBody ManualPlateRequest request) {
        ApiResponse<Map<String, Object>> response = apiController.assignManualPlate(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/detect/plate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detectPlate(
            @RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Please upload an image file (field name: image)", 400));
        }
        try {
            byte[] bytes = image.getBytes();
            ApiResponse<Map<String, Object>> response = apiController.detectLicensePlateFromImage(bytes);
            return ResponseEntity.status(response.getStatusCode()).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to read image: " + e.getMessage(), 500));
        }
    }

    /**
     * POST /api/v1/detect/vehicle/image
     * Poligon ROI: slotlar 4 noktalı poligon. OpenCV ile yeşil (boş) / kırmızı (dolu + OCCUPIED + skor) çizilmiş JPEG.
     * gRPC sunucu yanıt vermezse Java tarafında poligon çizimi fallback olarak kullanılır.
     */
    @PostMapping(value = "/detect/vehicle/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> detectVehicleImage(@RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] annotated = yoloInference.getParkingSlotsAnnotatedImage(imageBytes);
            if (annotated != null && annotated.length > 0) {
                return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(annotated);
            }
        } catch (Exception ignored) {
            // gRPC/Python kapalı veya hata: fallback
        }
        BufferedImage bufferedImage = null;
        try {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception ignored) {
        }
        if (bufferedImage == null) {
            return ResponseEntity.badRequest().build();
        }
        if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB && bufferedImage.getType() != BufferedImage.TYPE_INT_BGR) {
            BufferedImage rgb = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = rgb.createGraphics();
            try {
                g2.drawImage(bufferedImage, 0, 0, Color.WHITE, null);
            } finally {
                g2.dispose();
            }
            bufferedImage = rgb;
        }
        int imgW = bufferedImage.getWidth();
        int imgH = bufferedImage.getHeight();
        try {
            List<ParkingSlotResultDto> slots = yoloInference.detectParkingSlots(imageBytes);
            Graphics2D g = bufferedImage.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(3f));
                g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, imgH / 25)));
                FontMetrics fm = g.getFontMetrics();
                for (ParkingSlotResultDto slot : slots) {
                    java.util.List<double[]> corners = slot.getCorners();
                    int[] xPoints = null;
                    int[] yPoints = null;
                    if (corners != null && corners.size() == 4) {
                        xPoints = new int[4];
                        yPoints = new int[4];
                        for (int i = 0; i < 4; i++) {
                            xPoints[i] = (int) (corners.get(i)[0] * imgW);
                            yPoints[i] = (int) (corners.get(i)[1] * imgH);
                        }
                    }
                    int x = (int) (slot.getX() * imgW);
                    int y = (int) (slot.getY() * imgH);
                    int w = (int) (slot.getWidth() * imgW);
                    int h = (int) (slot.getHeight() * imgH);
                    if (slot.isOccupied()) {
                        g.setColor(new Color(220, 20, 20, 220));
                        if (xPoints != null) {
                            g.drawPolygon(xPoints, yPoints, 4);
                        } else {
                            g.drawRect(x, y, w, h);
                        }
                        g.setColor(Color.RED);
                        int textY = y + h + fm.getAscent() + 4;
                        g.drawString("OCCUPIED " + String.format("%.0f%%", slot.getConfidence() * 100), x, textY);
                    } else {
                        g.setColor(new Color(20, 180, 20, 220));
                        if (xPoints != null) {
                            g.drawPolygon(xPoints, yPoints, 4);
                        } else {
                            g.drawRect(x, y, w, h);
                        }
                        g.setColor(new Color(20, 140, 20));
                        int textY = y + h + fm.getAscent() + 4;
                        g.drawString("Boş", x, textY);
                    }
                }
            } finally {
                g.dispose();
            }
        } catch (Exception ignored) {
        }
        try {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(toJpegBytes(bufferedImage));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private static byte[] toJpegBytes(BufferedImage img) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<javax.imageio.ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) {
            ImageIO.write(img, "jpeg", out);
            return out.toByteArray();
        }
        ImageWriter writer = it.next();
        try {
            ImageOutputStream ios = ImageIO.createImageOutputStream(out);
            if (ios == null) {
                ImageIO.write(img, "jpeg", out);
                return out.toByteArray();
            }
            try (ImageOutputStream stream = ios) {
                writer.setOutput(stream);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.9f);
                }
                writer.write(null, new IIOImage(img, null, null), param);
            }
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
