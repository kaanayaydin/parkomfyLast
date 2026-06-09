package com.parkomfy.api;

import java.util.ArrayList;
import java.util.List;

public class PredictedSlotDto {
    private int slotNumber;
    private List<SlotCornerDto> corners = new ArrayList<>();
    private boolean occupied;
    private double confidence;
    private String className;

    public int getSlotNumber() { return slotNumber; }
    public void setSlotNumber(int slotNumber) { this.slotNumber = slotNumber; }
    public List<SlotCornerDto> getCorners() { return corners; }
    public void setCorners(List<SlotCornerDto> corners) { this.corners = corners; }
    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
}
