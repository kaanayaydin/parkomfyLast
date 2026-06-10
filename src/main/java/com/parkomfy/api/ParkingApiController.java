package com.parkomfy.api;

import com.parkomfy.model.*;
import com.parkomfy.service.*;
import com.parkomfy.repository.IParkingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API Controller for Parking Operations
 * Handles mobile client requests for parking management
 * 
 * Base URL: /api/v1
 */
public class ParkingApiController {

    private static final Logger log = LoggerFactory.getLogger(ParkingApiController.class);

    private final IParkingService parkingService;
    private final IPaymentService paymentService;
    private final IDetectionService detectionService;
    private final IParkingRepository repository;
    private final ReservationService reservationService;
    private final LiveParkingService liveParkingService;
    private final PlateTrackingService plateTrackingService;
    private final NotificationService notificationService;
    private final ParkingEventBroadcaster broadcaster;
    private final ParkingSetupService parkingSetupService;
    private final PlateSimulationService plateSimulationService;
    private final OccupancySyncService occupancySyncService;
    private final ReservationAvailabilityService reservationAvailabilityService;
    
    public ParkingApiController(IParkingService parkingService,
                               IPaymentService paymentService,
                               IDetectionService detectionService,
                               IParkingRepository repository,
                               ReservationService reservationService,
                               LiveParkingService liveParkingService,
                               PlateTrackingService plateTrackingService,
                               NotificationService notificationService,
                               ParkingEventBroadcaster broadcaster,
                               ParkingSetupService parkingSetupService,
                               PlateSimulationService plateSimulationService,
                               OccupancySyncService occupancySyncService,
                               ReservationAvailabilityService reservationAvailabilityService) {
        this.parkingService = parkingService;
        this.paymentService = paymentService;
        this.detectionService = detectionService;
        this.repository = repository;
        this.reservationService = reservationService;
        this.liveParkingService = liveParkingService;
        this.plateTrackingService = plateTrackingService;
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
        this.parkingSetupService = parkingSetupService;
        this.plateSimulationService = plateSimulationService;
        this.occupancySyncService = occupancySyncService;
        this.reservationAvailabilityService = reservationAvailabilityService;
    }

    /** Admin video overlay — anlık hibrit tespit (CMD logları ile aynı kaynak). */
    public ApiResponse<LiveParkingStatusDto> getLiveHybridStatus(String areaId) {
        try {
            LiveParkingStatusDto dto = occupancySyncService.getLiveHybridStatus(areaId);
            if (dto == null) {
                return ApiResponse.error("Area not found", 404);
            }
            return ApiResponse.success(dto, "Live hybrid status");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
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
            LiveParkingStatusDto live = repository.isAreaCalibrated(areaId)
                ? occupancySyncService.getLiveHybridStatus(areaId)
                : liveParkingService.getLiveStatus(areaId, null, null);
            if (live == null) {
                return ApiResponse.error("Parking area not found", 404);
            }
            ParkingStatusDto status = new ParkingStatusDto();
            status.setAreaId(live.getAreaId());
            status.setAreaName(live.getAreaName());
            status.setTotalSlots(live.getTotalSlots());
            status.setAvailableSlots(live.getAvailableSlots());
            status.setOccupiedSlots(live.getOccupiedSlots());
            status.setOccupancyRate(live.getOccupancyRate());
            status.setLastUpdated(live.getLastUpdated());
            return ApiResponse.success(status, "Status retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving parking status: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<LiveParkingStatusDto> getLiveParkingStatus(String areaId, String startTime, String endTime) {
        try {
            LocalDateTime start = startTime != null && !startTime.isBlank() ? parseDateTime(startTime) : LocalDateTime.now();
            LocalDateTime end = endTime != null && !endTime.isBlank() ? parseDateTime(endTime) : start.plusHours(2);
            LiveParkingStatusDto live = repository.isAreaCalibrated(areaId)
                ? occupancySyncService.getLiveHybridStatus(areaId, start, end)
                : liveParkingService.getLiveStatus(areaId, start, end);
            if (live == null) {
                return ApiResponse.error("Parking area not found", 404);
            }
            return ApiResponse.success(live, "Live status retrieved");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving live status: " + e.getMessage(), 500);
        }
    }

    /**
     * GET /api/v1/parking/reservation-view
     * Rezervasyon zaman çizelgesi + sıkı çakışma kontrolü (canlı doluluk ayrı).
     */
    public ApiResponse<AreaReservationViewDto> getReservationView(String areaId, String startTime,
                                                                  String endTime, String timelineStart,
                                                                  String timelineEnd) {
        try {
            LocalDateTime start = parseDateTime(startTime);
            LocalDateTime end = parseDateTime(endTime);
            LocalDateTime tlStart = timelineStart != null && !timelineStart.isBlank()
                ? parseDateTime(timelineStart) : null;
            LocalDateTime tlEnd = timelineEnd != null && !timelineEnd.isBlank()
                ? parseDateTime(timelineEnd) : null;
            AreaReservationViewDto view = reservationAvailabilityService.getReservationView(
                areaId, start, end, tlStart, tlEnd);
            if (view == null) {
                return ApiResponse.error("Parking area not found", 404);
            }
            return ApiResponse.success(view, "Reservation view retrieved");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving reservation view: " + e.getMessage(), 500);
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
            
            String vehicleTypeRaw = request.getVehicleType();
            if (vehicleTypeRaw == null || vehicleTypeRaw.isBlank()) {
                vehicleTypeRaw = "CAR";
            }
            Vehicle.VehicleType vehicleType;
            try {
                vehicleType = Vehicle.VehicleType.valueOf(vehicleTypeRaw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ApiResponse.error("Invalid vehicleType: " + vehicleTypeRaw, 400);
            }

            // Create vehicle
            LicensePlate licensePlate = new LicensePlate(request.getLicensePlate());
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
            
            // Complete session and calculate fee (null when fee <= 0 / free minutes)
            Payment payment = parkingService.completeSession(session);

            session.setStatus(ParkingSession.SessionStatus.COMPLETED);
            repository.updateSession(session);

            if (payment == null) {
                PaymentDto freeExit = new PaymentDto();
                freeExit.setSessionId(sessionId);
                freeExit.setAmount(0.0);
                freeExit.setCurrency("TRY");
                freeExit.setStatus("COMPLETED");
                freeExit.setTimestamp(LocalDateTime.now());
                return ApiResponse.success(freeExit, "Parking session completed (no charge)", 200);
            }

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
    
    /**
     * GET /api/v1/payments?licensePlate=
     * Ödemelerim: giriş-çıkış park ücretleri (süre + tutar).
     */
    public ApiResponse<List<PaymentHistoryDto>> getMyPayments(String licensePlate) {
        try {
            if (licensePlate == null || licensePlate.isBlank()) {
                return ApiResponse.error("licensePlate is required", 400);
            }
            String normalized = com.parkomfy.util.PlateMatcher.normalize(licensePlate);
            List<PaymentHistoryDto> list = repository.getPaymentsByPlate(normalized).stream()
                .map(this::toPaymentHistory)
                .collect(java.util.stream.Collectors.toList());
            return ApiResponse.success(list, "Payments retrieved");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving payments: " + e.getMessage(), 500);
        }
    }

    private PaymentHistoryDto toPaymentHistory(Payment payment) {
        ParkingSession session = payment.getParkingSession();
        String areaId = payment.getStoredAreaId();
        String plate = payment.getStoredLicensePlate();
        if (session != null) {
            if (areaId == null && session.getParkingSlot() != null) {
                areaId = repository.getAreaIdForSlot(session.getParkingSlot().getSlotId());
            }
            if (session.getVehicle() != null) {
                if (areaId == null) {
                    areaId = session.getVehicle().getCurrentAreaId();
                }
                if (plate == null && session.getVehicle().getLicensePlate() != null) {
                    plate = session.getVehicle().getLicensePlate().getPlateNumber();
                }
            }
        }
        String areaName = null;
        if (areaId != null) {
            ParkingArea area = repository.getArea(areaId);
            if (area != null) {
                areaName = area.getAreaName();
            }
        }
        PaymentHistoryDto dto = PaymentHistoryDto.from(payment, areaName, plate);
        dto.setAreaId(areaId);
        return dto;
    }

    /**
     * POST /api/v1/payments/{paymentId}/pay
     * Walk-in park ücretini öde (Stripe simülasyonu).
     */
    public ApiResponse<PaymentResultDto> payWalkInPayment(String paymentId) {
        try {
            Payment payment = repository.getPayment(paymentId);
            if (payment == null) {
                return ApiResponse.error("Payment not found", 404);
            }
            if (!payment.isPending()) {
                return ApiResponse.error("Payment is not pending", 409);
            }
            if (payment.getAmount() <= 0) {
                return ApiResponse.error("Nothing to pay", 400);
            }
            ParkingSession session = payment.getParkingSession();
            if (session == null) {
                return ApiResponse.error("Parking session not found for payment", 400);
            }
            session.setPayment(payment);

            PaymentMethod.PaymentType paymentType = PaymentMethod.PaymentType.CREDIT_CARD;
            PaymentMethod paymentMethod = new PaymentMethod(paymentType);
            paymentMethod.setStripePaymentMethodId("pm_demo_walkin");

            Payment processed = paymentService.processStripePayment(session, paymentMethod, payment.getAmount());
            if (!processed.isCompleted()) {
                return ApiResponse.error("Payment processing failed", 500);
            }

            PaymentResultDto result = new PaymentResultDto();
            result.setSuccess(true);
            result.setPaymentId(processed.getPaymentId());
            result.setAmount(processed.getAmount());
            result.setStatus("COMPLETED");
            result.setTimestamp(LocalDateTime.now());
            return ApiResponse.success(result, "Payment completed", 200);
        } catch (Exception e) {
            return ApiResponse.error("Payment failed: " + e.getMessage(), 500);
        }
    }

    /**
     * GET /api/v1/payments/pending
     * List pending walk-in / exit payments for a license plate.
     */
    public ApiResponse<List<PaymentDto>> getPendingPayments(String licensePlate) {
        try {
            if (licensePlate == null || licensePlate.isBlank()) {
                return ApiResponse.error("licensePlate is required", 400);
            }
            String normalized = com.parkomfy.util.PlateMatcher.normalize(licensePlate);
            List<PaymentDto> dtos = repository.getPendingPaymentsByPlate(normalized).stream()
                .map(PaymentDto::new)
                .collect(java.util.stream.Collectors.toList());
            return ApiResponse.success(dtos, "Pending payments retrieved");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving pending payments: " + e.getMessage(), 500);
        }
    }
    
    // ============================================
    // AVAILABLE SLOTS ENDPOINT
    // ============================================
    
    /**
     * GET /api/v1/parking/slots/available
     * Get list of available parking slots
     */
    public ApiResponse<List<ParkingSlotDto>> getAvailableSlots(String areaId, String startTime, String endTime) {
        try {
            ParkingArea area = repository.getArea(areaId);
            if (area == null) {
                return ApiResponse.error("Parking area not found", 404);
            }

            List<ParkingSlot> slots;
            if (startTime != null && !startTime.isBlank() && endTime != null && !endTime.isBlank()) {
                LocalDateTime start = parseDateTime(startTime);
                LocalDateTime end = parseDateTime(endTime);
                slots = reservationService.findAvailableSlotsForRange(areaId, start, end);
            } else {
                slots = parkingService.findAvailableSlots(area);
            }
            
            List<ParkingSlotDto> availableSlots = slots.stream()
                .map(ParkingSlotDto::new)
                .collect(Collectors.toList());
            
            return ApiResponse.success(availableSlots, "Available slots retrieved successfully");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving available slots: " + e.getMessage(), 500);
        }
    }

    /**
     * POST /api/v1/parking/reservations
     * Reserve a slot for a specific time range.
     */
    public ApiResponse<ReservationDto> createReservation(CreateReservationRequest request) {
        try {
            if (request.getSlotId() == null || request.getSlotId().isBlank()) {
                return ApiResponse.error("slotId is required", 400);
            }
            if (request.getAreaId() == null || request.getAreaId().isBlank()) {
                return ApiResponse.error("areaId is required", 400);
            }
            if (request.getLicensePlate() == null || request.getLicensePlate().isBlank()) {
                return ApiResponse.error("licensePlate is required", 400);
            }
            LocalDateTime start = parseDateTime(request.getStartTime());
            LocalDateTime end = parseDateTime(request.getEndTime());

            PricingPolicy pricing = parkingService.getPricingPolicyForArea(request.getAreaId());
            SlotReservation reservation = reservationService.createReservation(
                request.getAreaId(),
                request.getSlotId(),
                request.getLicensePlate(),
                start,
                end,
                pricing);

            LiveParkingStatusDto live = liveParkingService.getLiveStatus(request.getAreaId(), start, end);
            broadcaster.broadcastLiveStatus(live);
            notificationService.sendToPlate(request.getLicensePlate(),
                "Rezervasyon onaylandı",
                request.getSlotId() + " slotu " + start + " - " + end + " arası rezerve edildi.");

            return ApiResponse.success(new ReservationDto(reservation), "Reservation created", 201);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 400);
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage(), 409);
        } catch (Exception e) {
            return ApiResponse.error("Error creating reservation: " + e.getMessage(), 500);
        }
    }

    /**
     * POST /api/v1/parking/reservations/{reservationId}/cancel
     * Cancel a reservation owned by the given license plate.
     */
    public ApiResponse<ReservationDto> cancelReservation(String reservationId, String licensePlate) {
        try {
            SlotReservation reservation = reservationService.cancelReservation(reservationId, licensePlate);
            LiveParkingStatusDto live = repository.isAreaCalibrated(reservation.getAreaId())
                ? occupancySyncService.getLiveHybridStatus(reservation.getAreaId())
                : liveParkingService.getLiveStatus(reservation.getAreaId(), null, null);
            if (live != null) {
                broadcaster.broadcastLiveStatus(live);
            }
            notificationService.sendToPlate(licensePlate,
                "Rezervasyon iptal edildi",
                reservation.getSlotId() + " slotu için rezervasyonunuz iptal edildi.");
            return ApiResponse.success(new ReservationDto(reservation), "Reservation cancelled");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 404);
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage(), 409);
        } catch (Exception e) {
            return ApiResponse.error("Error cancelling reservation: " + e.getMessage(), 500);
        }
    }

    /**
     * GET /api/v1/parking/reservations
     * List reservations for a license plate.
     */
    public ApiResponse<List<ReservationDto>> getReservations(String licensePlate) {
        try {
            if (licensePlate == null || licensePlate.isBlank()) {
                return ApiResponse.error("licensePlate is required", 400);
            }
            List<ReservationDto> list = reservationService.getReservationsByPlate(licensePlate).stream()
                .map(ReservationDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success(list, "Reservations retrieved");
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving reservations: " + e.getMessage(), 500);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        String normalized = value.length() > 19 ? value.substring(0, 19) : value;
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid datetime format: " + value);
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

    // ============================================
    // DETECTION TEST ENDPOINTS (camera image)
    // ============================================

    /**
     * Test vehicle detection with an uploaded parking/camera image.
     * Requires YOLO gRPC server on 127.0.0.1:50051 for real detection.
     */
    public ApiResponse<Map<String, Object>> detectVehicleFromImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ApiResponse.error("Image data is required", 400);
        }
        try {
            Camera camera = new Camera("TEST-CAM-1", "Test Camera", Camera.CameraType.PARKING_AREA, "Test");
            camera.setCurrentFrame(imageBytes);
            ParkingSlot dummySlot = new ParkingSlot("TEST-SLOT-1", 1, "A", 1, 0.0, 0.0);
            DetectionResult result = detectionService.detectOccupancy(camera, dummySlot);
            Map<String, Object> data = new HashMap<>();
            data.put("vehicleDetected", result.isVehicleDetected());
            data.put("confidence", result.getConfidence());
            data.put("detectionId", result.getDetectionId());
            data.put("cameraId", result.getCameraId());
            data.put("slotId", result.getSlotId());
            data.put("demoMode", false);
            return ApiResponse.success(data, "Detection completed");
        } catch (Exception e) {
            log.warn("gRPC araç tespiti başarısız, demo dönülüyor. Hata: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            Map<String, Object> demo = new HashMap<>();
            demo.put("vehicleDetected", true);
            demo.put("confidence", 0.85);
            demo.put("detectionId", "DEMO-" + System.currentTimeMillis());
            demo.put("cameraId", "TEST-CAM-1");
            demo.put("slotId", "TEST-SLOT-1");
            demo.put("demoMode", true);
            return ApiResponse.success(demo, "Demo sonuç (YOLO sunucusu kapalı; gerçek tespit için 127.0.0.1:50051 gRPC gerekli)");
        }
    }

    /**
     * Test license plate detection with an uploaded image (e.g. entrance camera).
     * Requires YOLO gRPC server on 127.0.0.1:50051 for real detection.
     */
    public ApiResponse<Map<String, Object>> detectLicensePlateFromImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ApiResponse.error("Image data is required", 400);
        }
        try {
            Camera camera = new Camera("TEST-LPR-1", "Test LPR Camera", Camera.CameraType.ENTRANCE_LPR, "Entrance");
            camera.setCurrentFrame(imageBytes);
            DetectionResult result = detectionService.detectLicensePlate(camera);
            Map<String, Object> data = new HashMap<>();
            data.put("licensePlateText", result.getLicensePlateText() != null ? result.getLicensePlateText() : "");
            data.put("confidence", result.getConfidence());
            data.put("detectionId", result.getDetectionId());
            data.put("cameraId", result.getCameraId());
            data.put("demoMode", false);
            return ApiResponse.success(data, "License plate detection completed");
        } catch (Exception e) {
            log.warn("gRPC plaka tespiti başarısız, demo dönülüyor. Hata: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            Map<String, Object> demo = new HashMap<>();
            demo.put("licensePlateText", "34 ABC 123");
            demo.put("confidence", 0.82);
            demo.put("detectionId", "DEMO-" + System.currentTimeMillis());
            demo.put("cameraId", "TEST-LPR-1");
            demo.put("demoMode", true);
            return ApiResponse.success(demo, "Demo sonuç (YOLO sunucusu kapalı; gerçek tespit için 127.0.0.1:50051 gRPC gerekli)");
        }
    }

    public ApiResponse<EntrancePlateResultDto> processEntrancePlate(byte[] imageBytes) {
        try {
            EntrancePlateResultDto result = plateTrackingService.processEntrance(imageBytes);
            if (result.getLicensePlate() == null || result.getLicensePlate().isBlank()) {
                return ApiResponse.error("Plaka okunamadı", 422);
            }
            return ApiResponse.success(result, "Giriş plakası kaydedildi");
        } catch (Exception e) {
            return ApiResponse.error("Giriş plaka işleme hatası: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<ExitPlateResultDto> processExitPlate(byte[] imageBytes) {
        try {
            ExitPlateResultDto result = plateTrackingService.processExit(imageBytes);
            if (!result.isExited()) {
                return ApiResponse.error("Çıkış plakası eşleşmedi veya okunamadı", 422);
            }
            return ApiResponse.success(result, "Araç çıkışı kaydedildi");
        } catch (Exception e) {
            return ApiResponse.error("Çıkış plaka işleme hatası: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<ParkingScanResultDto> processParkingCameraScan(byte[] imageBytes, String areaId) {
        try {
            if (areaId == null || areaId.isBlank()) {
                return ApiResponse.error("areaId is required", 400);
            }
            ParkingScanResultDto result = plateTrackingService.processParkingScan(imageBytes, areaId);
            return ApiResponse.success(result, "Park kamerası taraması tamamlandı");
        } catch (Exception e) {
            return ApiResponse.error("Park kamerası tarama hatası: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<String> registerPushToken(RegisterPushTokenRequest request) {
        try {
            if (request.getUserId() == null || request.getExpoPushToken() == null) {
                return ApiResponse.error("userId and expoPushToken required", 400);
            }
            notificationService.registerPushToken(request.getUserId(), request.getExpoPushToken());
            return ApiResponse.success("OK", "Push token kaydedildi");
        } catch (Exception e) {
            return ApiResponse.error("Push token kayıt hatası: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<List<ReservationDto>> getAdminReservations(String areaId) {
        try {
            List<SlotReservation> raw = (areaId == null || areaId.isBlank())
                ? repository.getAllReservations()
                : repository.getReservationsForArea(areaId);
            List<ReservationDto> list = raw.stream()
                .map(ReservationDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success(list, areaId == null || areaId.isBlank()
                ? "All reservations" : "Area reservations");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<List<ParkingSessionDto>> getAdminSessions(String areaId) {
        try {
            List<ParkingSessionDto> list = repository.getActiveSessions().stream()
                .filter(s -> s.getParkingSlot() != null
                    && s.getParkingSlot().getSlotId() != null
                    && s.getParkingSlot().getSlotId().contains(areaIdToLotKey(areaId)))
                .map(ParkingSessionDto::new)
                .collect(Collectors.toList());
            return ApiResponse.success(list, "Active sessions");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<List<DetectionLogDto>> getAdminDetections(String areaId) {
        try {
            List<DetectionLogDto> list = repository.getRecentDetections(areaId, 30).stream()
                .map(d -> {
                    DetectionLogDto dto = new DetectionLogDto();
                    dto.setDetectionId(d.getDetectionId());
                    dto.setCameraId(d.getCameraId());
                    dto.setSlotId(d.getSlotId());
                    dto.setDetectionType(d.getDetectionType() != null ? d.getDetectionType().name() : "OCCUPANCY");
                    dto.setLicensePlate(d.getLicensePlateText());
                    dto.setConfidence(d.getConfidence());
                    dto.setDetectionTime(d.getDetectionTime());
                    return dto;
                })
                .collect(Collectors.toList());
            return ApiResponse.success(list, "Recent detections");
        } catch (Exception e) {
            return ApiResponse.error("Error: " + e.getMessage(), 500);
        }
    }

    private String areaIdToLotKey(String areaId) {
        return repository.getLotKey(areaId);
    }

    public ApiResponse<List<ParkingAreaDto>> listParkingAreas() {
        try {
            return ApiResponse.success(parkingSetupService.listAreas(), "Parking areas");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
    }

    public ApiResponse<ParkingAreaDto> createParkingArea(CreateParkingAreaRequest request) {
        try {
            ParkingAreaDto dto = parkingSetupService.createParkingArea(request);
            return ApiResponse.success(dto, "Otopark oluşturuldu", 201);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
    }

    public ApiResponse<Map<String, Object>> resetParkingData() {
        try {
            parkingSetupService.resetParkingData();
            Map<String, Object> body = new HashMap<>();
            body.put("cleared", true);
            return ApiResponse.success(body, "Tüm otoparklar ve rezervasyonlar silindi (kullanıcılar korundu)");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
    }

    public ApiResponse<SlotPredictionResultDto> predictSlotLayout(byte[] imageBytes) {
        try {
            return ApiResponse.success(
                parkingSetupService.predictSlotsWithReference(imageBytes), "Model ön tahmini");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.error("Model tahmin hatası: " + e.getMessage(), 500);
        }
    }

    public ApiResponse<ParkingAreaDto> saveSlotCalibration(SaveCalibrationRequest request) {
        try {
            ParkingAreaDto dto = parkingSetupService.saveCalibration(request);
            LiveParkingStatusDto live = liveParkingService.getLiveStatus(request.getAreaId(), null, null);
            broadcaster.broadcastLiveStatus(live);
            return ApiResponse.success(dto, "Slot kalibrasyonu kaydedildi");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
    }

    public ApiResponse<List<ParkingAreaDto>> listPublicParkingAreas() {
        try {
            List<ParkingAreaDto> all = parkingSetupService.listAreas();
            List<ParkingAreaDto> publicList = all.stream()
                .filter(ParkingAreaDto::isCalibrated)
                .collect(Collectors.toList());
            return ApiResponse.success(publicList, "Public parking areas");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
    }

    public ApiResponse<Map<String, Object>> getEntryPlateStatus(String areaId) {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("mode", "SIMULATION");
            out.put("cameraActive", false);
            out.put("simulatedPlates", PlateSimulationService.SIMULATED_PLATES);

            String lotKey = (areaId != null && !areaId.isBlank()) ? areaIdToLotKey(areaId) : "";
            List<Map<String, Object>> inside = new ArrayList<>();
            for (ParkingSession session : repository.getActiveSessions()) {
                if (session.getParkingSlot() == null || session.getParkingSlot().getSlotId() == null) {
                    continue;
                }
                if (!lotKey.isEmpty() && !session.getParkingSlot().getSlotId().contains(lotKey)) {
                    continue;
                }
                if (session.getVehicle() == null || session.getVehicle().getLicensePlate() == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("licensePlate", session.getVehicle().getLicensePlate().getPlateNumber());
                row.put("slotId", session.getParkingSlot() != null ? session.getParkingSlot().getSlotId() : null);
                row.put("slotNumber", session.getParkingSlot() != null ? session.getParkingSlot().getSlotNumber() : 0);
                row.put("sessionId", session.getSessionId());
                row.put("entryTime", session.getEntryTime() != null ? session.getEntryTime().toString() : null);
                inside.add(row);
            }
            out.put("parkedVehicles", inside);
            return ApiResponse.success(out, "Entry plate simulation");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
    }

    public ApiResponse<Map<String, Object>> assignManualPlate(ManualPlateRequest req) {
        try {
            if (req.getAreaId() == null || req.getSlotId() == null
                || req.getLicensePlate() == null || req.getLicensePlate().isBlank()) {
                return ApiResponse.error("areaId, slotId and licensePlate required", 400);
            }
            ParkingSlot slot = repository.getSlot(req.getSlotId());
            if (slot == null) {
                return ApiResponse.error("Slot not found", 404);
            }
            Vehicle vehicle = plateSimulationService.getOrCreateVehicle(req.getLicensePlate().trim());
            ParkingSession existing = repository.getActiveSessionForSlot(slot.getSlotId());
            if (existing != null) {
                existing.setStatus(ParkingSession.SessionStatus.COMPLETED);
                repository.updateSession(existing);
            }
            if (slot.isAvailable()) {
                slot.occupy(vehicle);
            } else {
                slot.setStatus(ParkingSlot.SlotStatus.OCCUPIED);
                slot.setCurrentVehicle(vehicle);
            }
            ParkingSession session = new ParkingSession(vehicle, slot);
            repository.saveSession(session);
            repository.updateSlot(slot);

            LiveParkingStatusDto live = liveParkingService.getLiveStatus(req.getAreaId(), null, null);
            broadcaster.broadcastLiveStatus(live);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("slotId", slot.getSlotId());
            out.put("licensePlate", vehicle.getLicensePlate().getPlateNumber());
            out.put("sessionId", session.getSessionId());
            return ApiResponse.success(out, "Plaka manuel atandı");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage(), 500);
        }
    }
}
