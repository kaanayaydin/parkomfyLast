package com.parkomfy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera class represents a security camera in the parking area
 * Tracks camera location, status, and monitored slots
 */
public class Camera {
    private String cameraId;
    private String cameraName;
    private CameraType type;
    private String location; // e.g., "Entrance", "Floor 1 Zone A"
    private CameraStatus status;
    private List<ParkingSlot> monitoredSlots;
    private double xCoordinate;
    private double yCoordinate;
    private boolean hasNightVision;
    
    public Camera(String cameraId, String cameraName, CameraType type, String location) {
        this.cameraId = cameraId;
        this.cameraName = cameraName;
        this.type = type;
        this.location = location;
        this.status = CameraStatus.ACTIVE;
        this.monitoredSlots = new ArrayList<>();
        this.hasNightVision = false;
    }
    
    public String getCameraId() {
        return cameraId;
    }
    
    public String getCameraName() {
        return cameraName;
    }
    
    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
    }
    
    public CameraType getType() {
        return type;
    }
    
    public void setType(CameraType type) {
        this.type = type;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public CameraStatus getStatus() {
        return status;
    }
    
    public void setStatus(CameraStatus status) {
        this.status = status;
    }
    
    public List<ParkingSlot> getMonitoredSlots() {
        return new ArrayList<>(monitoredSlots);
    }
    
    public void addMonitoredSlot(ParkingSlot slot) {
        if (!monitoredSlots.contains(slot)) {
            monitoredSlots.add(slot);
        }
    }
    
    public void removeMonitoredSlot(ParkingSlot slot) {
        monitoredSlots.remove(slot);
    }
    
    public double getXCoordinate() {
        return xCoordinate;
    }
    
    public void setXCoordinate(double xCoordinate) {
        this.xCoordinate = xCoordinate;
    }
    
    public double getYCoordinate() {
        return yCoordinate;
    }
    
    public void setYCoordinate(double yCoordinate) {
        this.yCoordinate = yCoordinate;
    }
    
    public boolean hasNightVision() {
        return hasNightVision;
    }
    
    public void setHasNightVision(boolean hasNightVision) {
        this.hasNightVision = hasNightVision;
    }
    
    public boolean isActive() {
        return status == CameraStatus.ACTIVE;
    }
    
    @Override
    public String toString() {
        return "Camera{" +
                "cameraId='" + cameraId + '\'' +
                ", cameraName='" + cameraName + '\'' +
                ", type=" + type +
                ", location='" + location + '\'' +
                ", status=" + status +
                ", monitoredSlots=" + monitoredSlots.size() +
                '}';
    }
    
    /**
     * Enum for camera types
     */
    public enum CameraType {
        ENTRANCE_LPR,  // License Plate Recognition at entrance
        PARKING_AREA   // General parking area monitoring
    }
    
    /**
     * Enum for camera status
     */
    public enum CameraStatus {
        ACTIVE,
        INACTIVE,
        MAINTENANCE,
        ERROR
    }
}
