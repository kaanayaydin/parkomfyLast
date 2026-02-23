package com.parkomfy.repository;

import com.parkomfy.model.*;

import java.util.List;

/**
 * Interface for parking data repository
 * Defines contract for database operations
 */
public interface IParkingRepository {
    
    // Parking Area operations
    void saveArea(ParkingArea area);
    ParkingArea getArea(String areaId);
    List<ParkingArea> getAllAreas();
    
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
}
