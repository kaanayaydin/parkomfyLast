package com.parkomfy.api;

/**
 * Request DTO for creating parking session
 */
public class CreateSessionRequest {
    private String areaId;
    private String licensePlate;
    private String vehicleType;
    
    // Getters and Setters
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
}
