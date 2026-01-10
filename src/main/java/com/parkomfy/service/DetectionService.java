package com.parkomfy.service;

import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.ai.YOLOInference;
import com.parkomfy.model.*;
import com.parkomfy.ocr.ILicensePlateReader;
import com.parkomfy.ocr.LicensePlateReader;
import com.parkomfy.repository.IParkingRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * DetectionService - Business Logic Layer for Object Detection Operations
 * 
 * This service coordinates between:
 * - YOLOInference (AI layer): Handles YOLO model inference
 * - LicensePlateReader (OCR layer): Handles license plate text recognition
 * - IParkingRepository (Data layer): Persists detection results
 * 
 * Architecture Principles:
 * - Separation of Concerns: Business logic separated from AI/OCR implementation
 * - Dependency Injection: YOLO and OCR implementations are injected, not created internally
 * - Single Responsibility: This service only handles detection workflow, not model execution
 * 
 * Design Pattern: Service Layer Pattern with Dependency Injection
 */
public class DetectionService implements IDetectionService {
    
    private final IParkingRepository repository;
    private final IYOLOInference yoloInference;
    private final ILicensePlateReader licensePlateReader;
    
    private double totalDetections = 0;
    private double correctDetections = 0;
    
    /**
     * Constructor with dependency injection
     * 
     * @param repository Data repository for persisting detection results
     * @param yoloInference YOLO inference implementation (can be swapped: ONNX, Python API, DJL)
     * @param licensePlateReader OCR implementation (can be swapped: EasyOCR, Tesseract, Cloud API)
     */
    public DetectionService(IParkingRepository repository, 
                           IYOLOInference yoloInference, 
                           ILicensePlateReader licensePlateReader) {
        if (repository == null) {
            throw new IllegalArgumentException("Repository cannot be null");
        }
        if (yoloInference == null) {
            throw new IllegalArgumentException("YOLOInference cannot be null");
        }
        if (licensePlateReader == null) {
            throw new IllegalArgumentException("LicensePlateReader cannot be null");
        }
        this.repository = repository;
        this.yoloInference = yoloInference;
        this.licensePlateReader = licensePlateReader;
    }
    
    /**
     * Overloaded constructor - creates default implementations if not provided
     * Used for backward compatibility
     */
    public DetectionService(IParkingRepository repository) {
        this(repository, new YOLOInference(), new LicensePlateReader());
    }
    
    /**
     * Constructor for YOLO inference only (with default license plate reader)
     * Used when you have YOLO but want default license plate reader
     */
    public DetectionService(IParkingRepository repository, YOLOInference yoloInference) {
        this(repository, yoloInference, new LicensePlateReader());
    }
    
    @Override
    public DetectionResult detectOccupancy(Camera camera, ParkingSlot slot) {
        if (!camera.isActive()) {
            throw new IllegalStateException("Camera " + camera.getCameraId() + " is not active");
        }
        
        // Business Logic: Delegate AI inference to YOLO layer
        boolean vehicleDetected = yoloInference.detectVehicle(camera, slot);
        double confidence = yoloInference.getConfidence();
        
        // Create detection result model
        DetectionResult result = new DetectionResult(
            camera.getCameraId(),
            slot.getSlotId(),
            vehicleDetected,
            confidence
        );
        result.setDetectionType(DetectionResult.DetectionType.OCCUPANCY);
        
        // Update statistics for accuracy calculation
        totalDetections++;
        if (vehicleDetected == slot.isOccupied()) {
            correctDetections++;
        }
        
        // Persist detection result
        repository.saveDetectionResult(result);
        
        return result;
    }
    
    @Override
    public DetectionResult detectLicensePlate(Camera camera) {
        if (camera.getType() != Camera.CameraType.ENTRANCE_LPR) {
            throw new IllegalArgumentException("Camera must be ENTRANCE_LPR type");
        }
        
        // Step 1: YOLO detects license plate bounding box
        // Business Logic: Coordinate between YOLO (detection) and OCR (recognition)
        String boundingBox = yoloInference.detectLicensePlateBoundingBox(camera);
        double detectionConfidence = yoloInference.getConfidence();
        
        String licensePlateText = null;
        double ocrConfidence = 0.0;
        
        // Step 2: If bounding box detected, use OCR to read text
        if (boundingBox != null && !boundingBox.isEmpty()) {
            // In real implementation: Crop image using bounding box coordinates
            // For now, OCR reads directly from camera frame
            licensePlateText = licensePlateReader.readLicensePlate(camera);
            ocrConfidence = licensePlateReader.getConfidence();
            
            // Step 3: Validate license plate format (country-specific)
            if (licensePlateText != null) {
                // Validate Turkish format (can be extended for other countries)
                boolean isValid = licensePlateReader.validateLicensePlateFormat(licensePlateText, "TR");
                if (!isValid) {
                    // Log warning but still return the detected text
                    // In production, might retry or use alternative OCR
                }
            }
        }
        
        // Combine confidence scores (weighted average)
        double combinedConfidence = (detectionConfidence * 0.4) + (ocrConfidence * 0.6);
        
        DetectionResult result = new DetectionResult(
            camera.getCameraId(),
            null,
            licensePlateText != null && !licensePlateText.isEmpty(),
            combinedConfidence
        );
        
        result.setLicensePlateText(licensePlateText);
        result.setDetectionType(DetectionResult.DetectionType.LICENSE_PLATE);
        
        // Persist detection result
        repository.saveDetectionResult(result);
        
        return result;
    }
    
    @Override
    public void processDetectionResult(DetectionResult result) {
        if (result.getDetectionType() == DetectionResult.DetectionType.OCCUPANCY) {
            // Business Logic: Update slot status based on detection result
            ParkingSlot slot = repository.getSlot(result.getSlotId());
            if (slot != null) {
                if (result.isVehicleDetected() && result.isHighConfidence()) {
                    // Vehicle detected with high confidence (>80%)
                    if (!slot.isOccupied()) {
                        // Slot should be occupied but isn't marked as such
                        // This could indicate:
                        // 1. Vehicle just entered (needs LPR link)
                        // 2. Manual entry not yet processed
                        // In real system, this would trigger LPR detection and session creation
                    }
                } else if (!result.isVehicleDetected() && result.isHighConfidence()) {
                    // No vehicle detected with high confidence
                    // Slot appears empty but is marked as occupied
                    if (slot.isOccupied()) {
                        slot.vacate();
                        repository.updateSlot(slot);
                    }
                }
            }
        }
    }
    
    @Override
    public List<DetectionResult> batchDetect(Camera camera, List<ParkingSlot> slots) {
        List<DetectionResult> results = new ArrayList<>();
        
        for (ParkingSlot slot : slots) {
            DetectionResult result = detectOccupancy(camera, slot);
            results.add(result);
        }
        
        return results;
    }
    
    @Override
    public double getDetectionAccuracy() {
        if (totalDetections == 0) return 0.0;
        return (correctDetections / totalDetections) * 100.0;
    }
    
    /**
     * Gets the total number of detections performed
     * @return Total detection count
     */
    public long getTotalDetections() {
        return (long) totalDetections;
    }
    
    /**
     * Gets the number of correct detections
     * @return Correct detection count
     */
    public long getCorrectDetections() {
        return (long) correctDetections;
    }
}
