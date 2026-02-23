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
