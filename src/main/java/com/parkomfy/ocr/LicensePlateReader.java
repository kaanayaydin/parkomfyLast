package com.parkomfy.ocr;

import com.parkomfy.model.Camera;

import java.util.regex.Pattern;

/**
 * License plate OCR. Plate text can come from gRPC LPR; this class validates format and can call external OCR API.
 */
public class LicensePlateReader implements ILicensePlateReader {

    private double lastConfidence = 0.85;

    /** Turkish plate: 2 digits, 1–3 letters, 2–4 digits (e.g. 34ABC123). */
    private static final Pattern TURKISH_PLATE_PATTERN =
        Pattern.compile("^\\d{2}[A-Z]{1,3}\\d{2,4}$");

    public LicensePlateReader() {
        initializeOCR();
    }

    private void initializeOCR() {
        // Optional: load ocr.backend (easyocr_api, tesseract, etc.)
    }

    @Override
    public String readLicensePlate(Camera camera) {
        if (camera.getType() != Camera.CameraType.ENTRANCE_LPR) {
            throw new IllegalArgumentException("Camera must be ENTRANCE_LPR type");
        }
        if (camera.getCurrentFrame() == null || camera.getCurrentFrame().length == 0) {
            lastConfidence = 0.0;
            return null;
        }
        // TODO: call external OCR API when integrated
        lastConfidence = 0.0;
        return null;
    }

    @Override
    public String readLicensePlate(byte[] imageData, String boundingBox) {
        if (imageData == null || imageData.length == 0) {
            lastConfidence = 0.0;
            return null;
        }
        // TODO: call OCR API with cropped region
        lastConfidence = 0.0;
        return null;
    }

    @Override
    public double getConfidence() {
        return lastConfidence;
    }

    @Override
    public boolean validateLicensePlateFormat(String text, String countryCode) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        text = text.replaceAll("\\s+", "").toUpperCase();

        switch (countryCode.toUpperCase()) {
            case "TR":
                return TURKISH_PLATE_PATTERN.matcher(text).matches();

            case "US":
                return text.length() >= 6 && text.length() <= 8;

            default:
                return text.matches("^[A-Z0-9]{6,10}$");
        }
    }
}
