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

    public ParkingSlotResultDto(double x, double y, double width, double height, boolean occupied, double confidence,
                               List<double[]> corners) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.occupied = occupied;
        this.confidence = confidence;
        this.corners = corners != null ? corners : Collections.emptyList();
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public boolean isOccupied() { return occupied; }
    public double getConfidence() { return confidence; }
    /** 4 köşe varsa perspektif çokgen çizilir. */
    public List<double[]> getCorners() { return corners; }
}
