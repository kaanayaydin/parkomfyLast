package com.parkomfy.model;

import java.time.LocalDateTime;

/**
 * Vehicle class represents a vehicle in the parking system
 * Contains license plate, entry/exit times, and vehicle information
 */
public class Vehicle {
    private String vehicleId;
    private LicensePlate licensePlate;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private VehicleType vehicleType;
    private String userId; // Link to user account
    private String currentAreaId; // Otopark alanı (giriş / park taraması)
    
    public Vehicle(LicensePlate licensePlate) {
        this.vehicleId = generateVehicleId();
        this.licensePlate = licensePlate;
        this.vehicleType = VehicleType.CAR; // Default
        this.entryTime = LocalDateTime.now();
    }
    
    public Vehicle(LicensePlate licensePlate, VehicleType vehicleType) {
        this.vehicleId = generateVehicleId();
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.entryTime = LocalDateTime.now();
    }
    
    private String generateVehicleId() {
        return "VEH-" + System.currentTimeMillis() + "-" + 
               (int)(Math.random() * 1000);
    }
    
    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }
    
    public LicensePlate getLicensePlate() {
        return licensePlate;
    }
    
    public void setLicensePlate(LicensePlate licensePlate) {
        this.licensePlate = licensePlate;
    }
    
    public LocalDateTime getEntryTime() {
        return entryTime;
    }
    
    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }
    
    public LocalDateTime getExitTime() {
        return exitTime;
    }
    
    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }
    
    public VehicleType getVehicleType() {
        return vehicleType;
    }
    
    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCurrentAreaId() {
        return currentAreaId;
    }

    public void setCurrentAreaId(String currentAreaId) {
        this.currentAreaId = currentAreaId;
    }
    
    public long getParkingDurationMinutes() {
        if (exitTime == null) {
            return java.time.Duration.between(entryTime, LocalDateTime.now()).toMinutes();
        }
        return java.time.Duration.between(entryTime, exitTime).toMinutes();
    }
    
    public boolean isCurrentlyParked() {
        return exitTime == null;
    }
    
    @Override
    public String toString() {
        return "Vehicle{" +
                "vehicleId='" + vehicleId + '\'' +
                ", licensePlate=" + licensePlate +
                ", entryTime=" + entryTime +
                ", vehicleType=" + vehicleType +
                '}';
    }
    
    /**
     * Enum for vehicle types
     */
    public enum VehicleType {
        CAR,
        MOTORCYCLE,
        TRUCK,
        VAN
    }
}
