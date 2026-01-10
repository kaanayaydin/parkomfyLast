package com.parkomfy.api;

import com.parkomfy.model.ParkingSession;
import java.time.LocalDateTime;

/**
 * DTO for Parking Session
 */
public class ParkingSessionDto {
    private String sessionId;
    private String vehicleId;
    private String licensePlate;
    private String slotId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private int durationMinutes;
    private boolean completed;
    
    public ParkingSessionDto() {}
    
    public ParkingSessionDto(ParkingSession session) {
        this.sessionId = session.getSessionId();
        this.vehicleId = session.getVehicle().getVehicleId();
        this.licensePlate = session.getVehicle().getLicensePlate().getPlateNumber();
        this.slotId = session.getParkingSlot().getSlotId();
        this.entryTime = session.getEntryTime();
        this.exitTime = session.getExitTime();
        this.durationMinutes = (int) session.getDurationMinutes();
        this.completed = !session.isActive();
    }
    
    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
