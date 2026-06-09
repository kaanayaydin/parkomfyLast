package com.parkomfy.model;

import java.time.LocalDateTime;

/**
 * ParkingSession class represents a complete parking session
 * Links vehicle, slot, payment, and timing information
 */
public class ParkingSession {
    private String sessionId;
    private Vehicle vehicle;
    private ParkingSlot parkingSlot;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private Payment payment;
    private SessionStatus status;
    
    public ParkingSession(Vehicle vehicle, ParkingSlot parkingSlot) {
        this.sessionId = generateSessionId();
        this.vehicle = vehicle;
        this.parkingSlot = parkingSlot;
        this.entryTime = LocalDateTime.now();
        this.status = SessionStatus.ACTIVE;
    }
    
    private String generateSessionId() {
        return "SESSION-" + System.currentTimeMillis() + "-" + 
               (int)(Math.random() * 10000);
    }
    
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }
    
    public Vehicle getVehicle() {
        return vehicle;
    }
    
    public ParkingSlot getParkingSlot() {
        return parkingSlot;
    }
    
    public LocalDateTime getEntryTime() {
        return entryTime;
    }
    
    public LocalDateTime getExitTime() {
        return exitTime;
    }
    
    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
        if (exitTime != null) {
            this.status = SessionStatus.COMPLETED;
        }
    }
    
    public Payment getPayment() {
        return payment;
    }
    
    public void setPayment(Payment payment) {
        this.payment = payment;
    }
    
    public SessionStatus getStatus() {
        return status;
    }
    
    public void setStatus(SessionStatus status) {
        this.status = status;
    }
    
    public long getDurationMinutes() {
        LocalDateTime endTime = exitTime != null ? exitTime : LocalDateTime.now();
        return java.time.Duration.between(entryTime, endTime).toMinutes();
    }
    
    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public boolean isLeaving() {
        return status == SessionStatus.LEAVING;
    }

    public boolean isActiveOrLeaving() {
        return status == SessionStatus.ACTIVE || status == SessionStatus.LEAVING;
    }
    
    public void complete() {
        this.exitTime = LocalDateTime.now();
        this.status = SessionStatus.COMPLETED;
        if (parkingSlot != null) {
            parkingSlot.vacate();
        }
    }
    
    @Override
    public String toString() {
        return "ParkingSession{" +
                "sessionId='" + sessionId + '\'' +
                ", vehicle=" + vehicle.getLicensePlate() +
                ", slot=" + (parkingSlot != null ? parkingSlot.getSlotId() : "None") +
                ", entryTime=" + entryTime +
                ", status=" + status +
                '}';
    }
    
    /**
     * Enum for session status
     */
    public enum SessionStatus {
        ACTIVE,
        LEAVING,
        COMPLETED,
        CANCELLED
    }
}
