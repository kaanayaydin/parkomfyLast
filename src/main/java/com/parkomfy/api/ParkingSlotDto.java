package com.parkomfy.api;

import com.parkomfy.model.ParkingSlot;

/**
 * DTO for Parking Slot
 */
public class ParkingSlotDto {
    private String slotId;
    private int floor;
    private String zone;
    private String status;
    private String location;
    
    public ParkingSlotDto() {}
    
    public ParkingSlotDto(ParkingSlot slot) {
        this.slotId = slot.getSlotId();
        this.floor = slot.getFloorNumber();
        this.zone = slot.getZone();
        this.status = slot.getStatus().toString();
        this.location = slot.getLocationString();
    }
    
    // Getters and Setters
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
