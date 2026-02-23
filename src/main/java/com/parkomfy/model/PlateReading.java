package com.parkomfy.model;

/** Single-frame plate read (text + confidence from gRPC). Used by validatePlateWithMultiFrame. */
public class PlateReading {
    private final String plateText;
    private final double confidence;

    public PlateReading(String plateText, double confidence) {
        this.plateText = plateText;
        this.confidence = confidence;
    }

    public String getPlateText() {
        return plateText;
    }

    public double getConfidence() {
        return confidence;
    }
}
