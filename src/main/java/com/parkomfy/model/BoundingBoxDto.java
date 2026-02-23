package com.parkomfy.model;

/** YOLO detection box DTO (proto-agnostic, used for IoU and service layer). */
public class BoundingBoxDto {
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final double confidence;

    public BoundingBoxDto(double x, double y, double width, double height, double confidence) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.confidence = confidence;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getConfidence() { return confidence; }
}
