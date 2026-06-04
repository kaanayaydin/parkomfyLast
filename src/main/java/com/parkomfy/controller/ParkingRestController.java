package com.parkomfy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.parkomfy.api.*;
import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.model.ParkingSlotResultDto;
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
