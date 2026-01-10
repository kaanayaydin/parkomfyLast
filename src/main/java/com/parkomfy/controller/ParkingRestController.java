package com.parkomfy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.parkomfy.api.*;
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
}
