package com.parkomfy.ai;

import com.parkomfy.model.Camera;
import com.parkomfy.model.ParkingSlot;

/**
 * Interface for YOLO model inference operations
 * Supports different implementations: ONNX Runtime, Python REST API, or Deep Java Library (DJL)
 * 
 * This abstraction allows switching between different YOLO implementations
 * without changing the business logic layer.
 */
public interface IYOLOInference {
    
    /**
     * Detects if a vehicle is present in a parking slot
     * 
     * @param camera The camera providing the frame
     * @param slot The parking slot to check
     * @return true if vehicle is detected, false otherwise
     */
    boolean detectVehicle(Camera camera, ParkingSlot slot);
    
    /**
     * Detects license plate bounding box in the camera frame
     * (First step before OCR)
     * 
     * @param camera The camera providing the frame
     * @return Detected license plate text (raw detection, may need OCR refinement)
     */
    String detectLicensePlateBoundingBox(Camera camera);
    
    /**
     * Gets the confidence score of the last detection
     * 
     * @return Confidence score between 0.0 and 1.0
     */
    double getConfidence();
    
    /**
     * Processes a frame and returns detection results
     * This is the main entry point for YOLO inference
     * 
     * @param frameData Raw image frame data (bytes)
     * @return true if vehicle is detected, false otherwise
     */
    boolean processAndDetect(byte[] frameData);
}

