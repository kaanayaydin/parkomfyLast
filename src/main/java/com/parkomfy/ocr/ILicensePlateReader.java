package com.parkomfy.ocr;

import com.parkomfy.model.Camera;

/**
 * Interface for License Plate Recognition (OCR) operations
 * 
 * Supports different OCR backends:
 * - EasyOCR (Python library via REST API or JNI)
 * - Tesseract OCR (via Tess4J Java wrapper)
 * - Cloud OCR APIs (Google Vision, AWS Textract)
 * 
 * This abstraction allows switching OCR implementations
 * without affecting the business logic layer.
 */
public interface ILicensePlateReader {
    
    /**
     * Reads license plate text from camera frame
     * 
     * @param camera The camera providing the frame
     * @return Detected license plate text, or null if not detected
     */
    String readLicensePlate(Camera camera);
    
    /**
     * Reads license plate text from image bytes
     * 
     * @param imageData Raw image data (JPEG/PNG bytes)
     * @param boundingBox Bounding box coordinates from YOLO (JSON format)
     * @return Detected license plate text, or null if not detected
     */
    String readLicensePlate(byte[] imageData, String boundingBox);
    
    /**
     * Gets the confidence score of the last OCR operation
     * 
     * @return Confidence score between 0.0 and 1.0
     */
    double getConfidence();
    
    /**
     * Validates if the detected text matches license plate format
     * (Country-specific validation)
     * 
     * @param text Detected text
     * @param countryCode Country code (e.g., "TR" for Turkey)
     * @return true if valid format
     */
    boolean validateLicensePlateFormat(String text, String countryCode);
}

