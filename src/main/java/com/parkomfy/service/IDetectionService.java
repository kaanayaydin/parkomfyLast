package com.parkomfy.service;

import com.parkomfy.model.Camera;
import com.parkomfy.model.DetectionResult;
import com.parkomfy.model.ParkingSlot;

import java.util.List;

/**
 * Interface for YOLO detection service
 * Defines contract for computer vision operations
 */
public interface IDetectionService {
    
    /**
     * Detect vehicle occupancy in a parking slot
     */
    DetectionResult detectOccupancy(Camera camera, ParkingSlot slot);
    
    /**
     * Detect license plate from camera image
     */
    DetectionResult detectLicensePlate(Camera camera);
    
    /**
     * Process detection results and update slot status
     */
    void processDetectionResult(DetectionResult result);
    
    /**
     * Batch detect multiple slots
     */
    List<DetectionResult> batchDetect(Camera camera, List<ParkingSlot> slots);
    
    /**
     * Get detection accuracy metrics
     */
    double getDetectionAccuracy();
}
