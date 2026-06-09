package com.parkomfy.model;

import java.time.LocalDateTime;

/**
 * DetectionResult class represents the result of YOLO object detection
 * Contains detection confidence, timestamp, and detected objects
 */
public class DetectionResult {
    private String detectionId;
    private String cameraId;
    private String slotId;
    private boolean vehicleDetected;
    private double confidence; // 0.0 to 1.0
    private LocalDateTime detectionTime;
    private String licensePlateText; // Detected license plate (if any)
    private DetectionType detectionType;
    
    public DetectionResult(String cameraId, String slotId, boolean vehicleDetected, 
                          double confidence) {
        this.detectionId = generateDetectionId();
        this.cameraId = cameraId;
        this.slotId = slotId;
        this.vehicleDetected = vehicleDetected;
        this.confidence = confidence;
        this.detectionTime = LocalDateTime.now();
        this.detectionType = DetectionType.OCCUPANCY;
    }
    
    private String generateDetectionId() {
        return "DET-" + System.currentTimeMillis() + "-" + 
               (int)(Math.random() * 10000);
    }
    
    public String getDetectionId() {
        return detectionId;
    }

    public void setDetectionId(String detectionId) {
        this.detectionId = detectionId;
    }
    
    public String getCameraId() {
        return cameraId;
    }
    
    public void setCameraId(String cameraId) {
        this.cameraId = cameraId;
    }
    
    public String getSlotId() {
        return slotId;
    }
    
    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }
    
    public boolean isVehicleDetected() {
        return vehicleDetected;
    }
    
    public void setVehicleDetected(boolean vehicleDetected) {
        this.vehicleDetected = vehicleDetected;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = Math.max(0.0, Math.min(1.0, confidence)); // Clamp between 0 and 1
    }
    
    public LocalDateTime getDetectionTime() {
        return detectionTime;
    }
    
    public void setDetectionTime(LocalDateTime detectionTime) {
        this.detectionTime = detectionTime;
    }
    
    public String getLicensePlateText() {
        return licensePlateText;
    }
    
    public void setLicensePlateText(String licensePlateText) {
        this.licensePlateText = licensePlateText;
    }
    
    public DetectionType getDetectionType() {
        return detectionType;
    }
    
    public void setDetectionType(DetectionType detectionType) {
        this.detectionType = detectionType;
    }
    
    public boolean isHighConfidence() {
        return confidence >= 0.8; // 80% confidence threshold
    }
    
    @Override
    public String toString() {
        return "DetectionResult{" +
                "detectionId='" + detectionId + '\'' +
                ", slotId='" + slotId + '\'' +
                ", vehicleDetected=" + vehicleDetected +
                ", confidence=" + String.format("%.2f", confidence * 100) + "%" +
                ", detectionTime=" + detectionTime +
                '}';
    }
    
    /**
     * Enum for detection types
     */
    public enum DetectionType {
        OCCUPANCY,      // Vehicle occupancy detection
        LICENSE_PLATE   // License plate recognition
    }
}
