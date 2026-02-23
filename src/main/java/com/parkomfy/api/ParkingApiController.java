package com.parkomfy.api;

import com.parkomfy.model.*;
import com.parkomfy.service.*;
import com.parkomfy.repository.IParkingRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API Controller for Parking Operations
 * Handles mobile client requests for parking management
 * 
 * Base URL: /api/v1
 */
public class ParkingApiController {
    
    private final IParkingService parkingService;
    private final IPaymentService paymentService;
    private final IDetectionService detectionService;
    private final IParkingRepository repository;
    
    public ParkingApiController(IParkingService parkingService,
                               IPaymentService paymentService,
                               IDetectionService detectionService,
                               IParkingRepository repository) {
        this.parkingService = parkingService;
        this.paymentService = paymentService;
        this.detectionService = detectionService;
        this.repository = repository;
    }
    
    // ============================================
    // PARKING AREA ENDPOINTS
    // ============================================
    
    /**
     * GET /api/v1/parking/status
     * Get real-time parking area status
     */
    public ApiResponse<ParkingStatusDto> getParkingStatus(String areaId) {
        try {
            ParkingArea area = repository.getArea(areaId);
            if (area == null) {
                return ApiResponse.error("Parking area not found", 404);
            }
            
            ParkingStatusDto status = new ParkingStatusDto();
            status.setAreaId(area.getAreaId());
            status.setAreaName(area.getAreaName());
            status.setTotalSlots(area.getParkingSlots().size());
            status.setAvailableSlots(area.getAvailableSlotCount());
            status.setOccupiedSlots(area.getOccupiedSlotCount());
            status.setOccupancyRate((double) area.getOccupiedSlotCount() / area.getParkingSlots().size());
            status.setLastUpdated(LocalDateTime.now());
            
            return ApiResponse.success(status, "Status retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving parking status: " + e.getMessage(), 500);
        }
    }
    
    // ============================================
    // PARKING SESSION ENDPOINTS
    // ============================================
    
    /**
     * POST /api/v1/parking/sessions
     * Create new parking session (vehicle entry)
     */
    public ApiResponse<ParkingSessionDto> createParkingSession(CreateSessionRequest request) {
        try {
            // Validate request
            if (request.getLicensePlate() == null || request.getLicensePlate().trim().isEmpty()) {
                return ApiResponse.error("License plate is required", 400);
            }
            
            // Create vehicle
            LicensePlate licensePlate = new LicensePlate(request.getLicensePlate());
            Vehicle.VehicleType vehicleType = Vehicle.VehicleType.valueOf(
                request.getVehicleType().toUpperCase()
            );
            Vehicle vehicle = new Vehicle(licensePlate, vehicleType);
            
            // Save vehicle
            repository.saveVehicle(vehicle);
            
            // Find available slot
            ParkingArea area = repository.getArea(request.getAreaId());
            List<ParkingSlot> availableSlots = parkingService.findAvailableSlots(area);
            
            if (availableSlots.isEmpty()) {
                return ApiResponse.error("No available parking slots", 409);
            }
            
            // Occupy slot
            ParkingSlot selectedSlot = availableSlots.get(0);
            ParkingSession session = parkingService.occupySlot(selectedSlot, vehicle);
            
            // Save session
            repository.saveSession(session);
            
            // Return response
            ParkingSessionDto sessionDto = new ParkingSessionDto(session);
            return ApiResponse.success(sessionDto, "Parking session created successfully", 201);
            
        } catch (Exception e) {
            return ApiResponse.error("Error creating parking session: " + e.getMessage(), 500);
        }
    }
    
    /**
     * GET /api/v1/parking/sessions/{sessionId}
     * Get parking session details
     */
    public ApiResponse<ParkingSessionDto> getSession(String sessionId) {
        try {
            ParkingSession session = repository.getSession(sessionId);
            if (session == null) {
                return ApiResponse.error("Session not found", 404);
            }
            
            ParkingSessionDto sessionDto = new ParkingSessionDto(session);
            return ApiResponse.success(sessionDto, "Session retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving session: " + e.getMessage(), 500);
        }
    }
    
    /**
     * POST /api/v1/parking/sessions/{sessionId}/exit
     * Complete parking session (vehicle exit)
     */
    public ApiResponse<PaymentDto> exitParking(String sessionId) {
        try {
            ParkingSession session = repository.getSession(sessionId);
            if (session == null) {
                return ApiResponse.error("Session not found", 404);
            }
            
            // Complete session and calculate fee
            Payment payment = parkingService.completeSession(session);
            
            // Update session - use setStatus() instead of setCompleted()
            session.setStatus(ParkingSession.SessionStatus.COMPLETED);
            repository.updateSession(session);
            
            // Return payment info
            PaymentDto paymentDto = new PaymentDto(payment);
            return ApiResponse.success(paymentDto, "Parking session completed", 200);
            
        } catch (Exception e) {
            return ApiResponse.error("Error completing session: " + e.getMessage(), 500);
        }
    }
    
    // ============================================
    // PAYMENT ENDPOINTS
    // ============================================
    
    /**
     * POST /api/v1/payments
     * Process payment for parking session
     */
    public ApiResponse<PaymentResultDto> processPayment(PaymentRequest request) {
        try {
            // Validate payment method
            // Get parking session
            ParkingSession session = repository.getSession(request.getSessionId());
            if (session == null) {
                return ApiResponse.error("Parking session not found", 404);
            }
            
            PaymentMethod.PaymentType paymentType = PaymentMethod.PaymentType.valueOf(
                request.getPaymentMethod().toUpperCase()
            );
            
            PaymentMethod paymentMethod = new PaymentMethod(paymentType);
            if (request.getCardToken() != null) {
                paymentMethod.setStripePaymentMethodId(request.getCardToken());
            }
            
            // Process payment
            Payment processedPayment = paymentService.processPayment(
                session,
                paymentMethod,
                request.getAmount()
            );
            
            PaymentResultDto result = new PaymentResultDto();
            result.setSuccess(true);
            result.setPaymentId(processedPayment.getPaymentId());
            result.setAmount(request.getAmount());
            result.setStatus("COMPLETED");
            result.setTimestamp(LocalDateTime.now());
            
            return ApiResponse.success(result, "Payment processed successfully", 200);
            
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("Invalid payment method: " + e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.error("Payment processing failed: " + e.getMessage(), 500);
        }
    }
    
    /**
     * GET /api/v1/payments/{paymentId}
     * Get payment details
     */
    public ApiResponse<PaymentDto> getPayment(String paymentId) {
        try {
            Payment payment = repository.getPayment(paymentId);
            if (payment == null) {
                return ApiResponse.error("Payment not found", 404);
            }
            
            PaymentDto paymentDto = new PaymentDto(payment);
            return ApiResponse.success(paymentDto, "Payment retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving payment: " + e.getMessage(), 500);
        }
    }
    
    // ============================================
    // AVAILABLE SLOTS ENDPOINT
    // ============================================
    
    /**
     * GET /api/v1/parking/slots/available
     * Get list of available parking slots
     */
    public ApiResponse<List<ParkingSlotDto>> getAvailableSlots(String areaId) {
        try {
            ParkingArea area = repository.getArea(areaId);
            if (area == null) {
                return ApiResponse.error("Parking area not found", 404);
            }
            
            List<ParkingSlotDto> availableSlots = parkingService.findAvailableSlots(area)
                .stream()
                .map(ParkingSlotDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success(availableSlots, "Available slots retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving available slots: " + e.getMessage(), 500);
        }
    }
    
    // ============================================
    // ACTIVE SESSIONS ENDPOINT
    // ============================================
    
    /**
     * GET /api/v1/parking/sessions/active
     * Get all active parking sessions
     */
    public ApiResponse<List<ParkingSessionDto>> getActiveSessions() {
        try {
            List<ParkingSessionDto> activeSessions = repository.getActiveSessions()
                .stream()
                .map(ParkingSessionDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success(activeSessions, "Active sessions retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving active sessions: " + e.getMessage(), 500);
        }
    }
}
