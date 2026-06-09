package com.parkomfy.model;

import java.time.LocalDateTime;

/**
 * ParkingSlot class represents a single parking space
 * Tracks occupancy status, location, and associated vehicle
 */
public class ParkingSlot {
    private String slotId;
    private int floorNumber;
    private String zone; // e.g., "A", "B", "C"
    private int slotNumber;
    private SlotStatus status;
    private Vehicle currentVehicle;
    private LocalDateTime lastStatusUpdate;
    private double xCoordinate;
    private double yCoordinate;
    /** Slot rectangle for IoU (same coord system as YOLO). */
    private double slotWidth = 100.0;
    private double slotHeight = 50.0;
    /** Normalized 0-1 polygon corners (admin calibration). */
    private double c1x, c1y, c2x, c2y, c3x, c3y, c4x, c4y;

    public ParkingSlot(String slotId, int floorNumber, String zone, int slotNumber) {
        this.slotId = slotId;
        this.floorNumber = floorNumber;
        this.zone = zone;
        this.slotNumber = slotNumber;
        this.status = SlotStatus.AVAILABLE;
        this.lastStatusUpdate = LocalDateTime.now();
    }
    
    public ParkingSlot(String slotId, int floorNumber, String zone, int slotNumber, 
                      double xCoordinate, double yCoordinate) {
        this(slotId, floorNumber, zone, slotNumber);
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
    }
    
    public String getSlotId() {
        return slotId;
    }
    
    public int getFloorNumber() {
        return floorNumber;
    }
    
    public String getZone() {
        return zone;
    }
    
    public int getSlotNumber() {
        return slotNumber;
    }
    
    public SlotStatus getStatus() {
        return status;
    }
    
    public void setStatus(SlotStatus status) {
        this.status = status;
        this.lastStatusUpdate = LocalDateTime.now();
    }
    
    public Vehicle getCurrentVehicle() {
        return currentVehicle;
    }
    
    public void setCurrentVehicle(Vehicle vehicle) {
        this.currentVehicle = vehicle;
        if (vehicle != null) {
            this.status = SlotStatus.OCCUPIED;
        } else {
            this.status = SlotStatus.AVAILABLE;
        }
        this.lastStatusUpdate = LocalDateTime.now();
    }
    
    public LocalDateTime getLastStatusUpdate() {
        return lastStatusUpdate;
    }
    
    public double getXCoordinate() {
        return xCoordinate;
    }
    
    public void setXCoordinate(double xCoordinate) {
        this.xCoordinate = xCoordinate;
    }
    
    public double getYCoordinate() {
        return yCoordinate;
    }
    
    public void setYCoordinate(double yCoordinate) {
        this.yCoordinate = yCoordinate;
    }

    public double getSlotWidth() {
        return slotWidth;
    }

    public void setSlotWidth(double slotWidth) {
        this.slotWidth = slotWidth;
    }

    public double getSlotHeight() {
        return slotHeight;
    }

    public void setSlotHeight(double slotHeight) {
        this.slotHeight = slotHeight;
    }

    public boolean isAvailable() {
        return status == SlotStatus.AVAILABLE;
    }
    
    public boolean isOccupied() {
        return status == SlotStatus.OCCUPIED;
    }
    
    public void occupy(Vehicle vehicle) {
        if (isAvailable()) {
            setCurrentVehicle(vehicle);
        } else {
            throw new IllegalStateException("Slot " + slotId + " is already occupied");
        }
    }
    
    public void vacate() {
        this.currentVehicle = null;
        this.status = SlotStatus.AVAILABLE;
        this.lastStatusUpdate = LocalDateTime.now();
    }
    
    public double getC1x() { return c1x; }
    public void setC1x(double v) { this.c1x = v; }
    public double getC1y() { return c1y; }
    public void setC1y(double v) { this.c1y = v; }
    public double getC2x() { return c2x; }
    public void setC2x(double v) { this.c2x = v; }
    public double getC2y() { return c2y; }
    public void setC2y(double v) { this.c2y = v; }
    public double getC3x() { return c3x; }
    public void setC3x(double v) { this.c3x = v; }
    public double getC3y() { return c3y; }
    public void setC3y(double v) { this.c3y = v; }
    public double getC4x() { return c4x; }
    public void setC4x(double v) { this.c4x = v; }
    public double getC4y() { return c4y; }
    public void setC4y(double v) { this.c4y = v; }

    public boolean hasCalibratedCorners() {
        return c1x > 0 || c1y > 0 || c2x > 0;
    }

    public String getLocationString() {
        return "Floor " + floorNumber + ", Zone " + zone + ", Slot " + slotNumber;
    }
    
    @Override
    public String toString() {
        return "ParkingSlot{" +
                "slotId='" + slotId + '\'' +
                ", location='" + getLocationString() + '\'' +
                ", status=" + status +
                ", currentVehicle=" + (currentVehicle != null ? currentVehicle.getLicensePlate() : "None") +
                '}';
    }
    
    /**
     * Enum for parking slot status
     */
    public enum SlotStatus {
        AVAILABLE,
        OCCUPIED,
        RESERVED,
        MAINTENANCE
    }
}
