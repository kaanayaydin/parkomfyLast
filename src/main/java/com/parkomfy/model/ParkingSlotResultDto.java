package com.parkomfy.model;

import java.util.Collections;
import java.util.List;

/** Park slotu tespit sonucu: perspektif 4 köşe veya bbox, dolu mu, güven skoru. */
public class ParkingSlotResultDto {
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final boolean occupied;
    private final double confidence;
    /** Perspektif slot için 4 köşe (sol-üst, sağ-üst, sağ-alt, sol-alt); normalize 0-1. Boş ise dikdörtgen çizilir. */
    private final List<double[]> corners;
    private final int slotNumber;
    private final double vehicleX;
    private final double vehicleY;
    private final double vehicleWidth;
    private final double vehicleHeight;

    public ParkingSlotResultDto(double x, double y, double width, double height, boolean occupied, double confidence,
                               List<double[]> corners) {
        this(x, y, width, height, occupied, confidence, corners, 0, 0, 0, 0, 0);
    }

    public ParkingSlotResultDto(double x, double y, double width, double height, boolean occupied, double confidence,
                               List<double[]> corners, int slotNumber,
                               double vehicleX, double vehicleY, double vehicleWidth, double vehicleHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.occupied = occupied;
        this.confidence = confidence;
        this.corners = corners != null ? corners : Collections.emptyList();
        this.slotNumber = slotNumber;
        this.vehicleX = vehicleX;
        this.vehicleY = vehicleY;
        this.vehicleWidth = vehicleWidth;
        this.vehicleHeight = vehicleHeight;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public boolean isOccupied() { return occupied; }
    public double getConfidence() { return confidence; }
    public List<double[]> getCorners() { return corners; }
    public int getSlotNumber() { return slotNumber; }
    public double getVehicleX() { return vehicleX; }
    public double getVehicleY() { return vehicleY; }
    public double getVehicleWidth() { return vehicleWidth; }
    public double getVehicleHeight() { return vehicleHeight; }
    public boolean hasVehicleBBox() {
        return vehicleWidth > 0.01 && vehicleHeight > 0.01;
    }
}
