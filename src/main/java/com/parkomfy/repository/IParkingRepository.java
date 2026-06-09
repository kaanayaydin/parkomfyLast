package com.parkomfy.repository;

import com.parkomfy.model.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface for parking data repository
 * Defines contract for database operations
 */
public interface IParkingRepository {
    
    // Parking Area operations
    void saveArea(ParkingArea area);
    void saveAreaFull(ParkingArea area, String lotKey);
    ParkingArea getArea(String areaId);
    List<ParkingArea> getAllAreas();
    String nextAreaId();
    String getLotKey(String areaId);
    boolean isAreaCalibrated(String areaId);
    void markAreaCalibrated(String areaId, boolean calibrated);
    void deleteSlotsForArea(String areaId);
    void insertSlot(String areaId, ParkingSlot slot);
    
    // Parking Slot operations
    void saveSlot(ParkingSlot slot);
    void updateSlot(ParkingSlot slot);
    ParkingSlot getSlot(String slotId);
    List<ParkingSlot> getAllSlots(String areaId);
    
    // Vehicle operations
    void saveVehicle(Vehicle vehicle);
    Vehicle getVehicle(String vehicleId);
    Vehicle getVehicleByPlate(LicensePlate licensePlate);
    
    // Session operations
    void saveSession(ParkingSession session);
    void updateSession(ParkingSession session);
    ParkingSession getSession(String sessionId);
    List<ParkingSession> getActiveSessions();
    
    // Payment operations
    void savePayment(Payment payment);
    void updatePayment(Payment payment);
    Payment getPayment(String paymentId);
    
    // Detection Result operations
    void saveDetectionResult(DetectionResult result);
    List<DetectionResult> getDetectionResults(String slotId);
    
    // User operations
    void saveUser(User user);
    User getUser(String userId);
    User getUserByEmail(String email);

    // Reservation operations
    void saveReservation(SlotReservation reservation);
    void updateReservation(SlotReservation reservation);
    SlotReservation getReservation(String reservationId);
    List<SlotReservation> getReservationsByPlate(String licensePlate);
    List<SlotReservation> getReservationsForArea(String areaId);
    List<SlotReservation> getOverlappingReservations(String slotId, LocalDateTime start, LocalDateTime end);
    SlotReservation getActiveReservationByPlate(String licensePlate, LocalDateTime at);
    List<SlotReservation> getUpcomingReservations(LocalDateTime from, LocalDateTime to);

    // Session helpers
    ParkingSession getActiveSessionForSlot(String slotId);
    ParkingSession getActiveSessionByPlate(String normalizedPlate);

    // Vehicle helpers
    List<Vehicle> getRecentEnteredVehicles();

    // Push tokens
    void savePushToken(String userId, String expoPushToken);
    List<String> getPushTokensForUser(String userId);
    List<String> getPushTokensForPlate(String normalizedPlate);

    // Detection logs
    List<DetectionResult> getRecentDetections(String areaId, int limit);
}
