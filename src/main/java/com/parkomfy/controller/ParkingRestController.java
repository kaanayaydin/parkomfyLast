package com.parkomfy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.parkomfy.api.*;
import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.model.BoundingBoxDto;
import com.parkomfy.model.Camera;
import com.parkomfy.model.ParkingSlot;
import com.parkomfy.service.IDetectionService;

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
            @RequestParam String areaId) {
        ApiResponse<List<ParkingSlotDto>> response = apiController.getAvailableSlots(areaId);
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
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
     * Fotoğraf yükle; tespit edilen araçların çerçevelendiği ve altında güven skoru yazan resmi döndür (JPEG).
     */
    @PostMapping(value = "/detect/vehicle/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> detectVehicleImage(@RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] imageBytes;
        BufferedImage bufferedImage;
        try {
            imageBytes = image.getBytes();
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        if (bufferedImage == null) {
            return ResponseEntity.badRequest().build();
        }
        // Şeffaflık varsa RGB'ye çevir; JPEG düzgün yazılsın
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
            Camera camera = new Camera("TEST-CAM-1", "Test", Camera.CameraType.PARKING_AREA, "Test");
            camera.setCurrentFrame(imageBytes);
            ParkingSlot dummySlot = new ParkingSlot("TEST-SLOT-1", 1, "A", 1, 0.0, 0.0);
            detectionService.detectOccupancy(camera, dummySlot);

            List<BoundingBoxDto> boxes = yoloInference.getLastBoundingBoxes();
            Graphics2D g = bufferedImage.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(3f));

                for (BoundingBoxDto box : boxes) {
                    int x = (int) (box.getX() * imgW);
                    int y = (int) (box.getY() * imgH);
                    int w = (int) (box.getWidth() * imgW);
                    int h = (int) (box.getHeight() * imgH);
                    g.setColor(new Color(0, 255, 0, 200));
                    g.drawRect(x, y, w, h);
                    String label = String.format("%.0f%%", box.getConfidence() * 100);
                    g.setColor(Color.GREEN);
                    g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, imgH / 25)));
                    FontMetrics fm = g.getFontMetrics();
                    int textY = y + h + fm.getAscent() + 4;
                    g.drawString(label, x, textY);
                }
            } finally {
                g.dispose();
            }
        } catch (Exception ignored) {
            // Tespit hatası olsa bile çıktıyı orijinal resimle ver
        }

        try {
            byte[] jpeg = toJpegBytes(bufferedImage);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(jpeg);
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
