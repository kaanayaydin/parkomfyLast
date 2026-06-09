package com.parkomfy.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LiveParkingStatusDto {
    private String areaId;
    private String areaName;
    private int totalSlots;
    private int availableSlots;
    private int occupiedSlots;
    private int reservedSlots;
    private double occupancyRate;
    private LocalDateTime lastUpdated;
    private List<LiveSlotStatusDto> slots = new ArrayList<>();

    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public int getTotalSlots() { return totalSlots; }
    public void setTotalSlots(int totalSlots) { this.totalSlots = totalSlots; }
    public int getAvailableSlots() { return availableSlots; }
    public void setAvailableSlots(int availableSlots) { this.availableSlots = availableSlots; }
    public int getOccupiedSlots() { return occupiedSlots; }
    public void setOccupiedSlots(int occupiedSlots) { this.occupiedSlots = occupiedSlots; }
    public int getReservedSlots() { return reservedSlots; }
    public void setReservedSlots(int reservedSlots) { this.reservedSlots = reservedSlots; }
    public double getOccupancyRate() { return occupancyRate; }
    public void setOccupancyRate(double occupancyRate) { this.occupancyRate = occupancyRate; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    public List<LiveSlotStatusDto> getSlots() { return slots; }
    public void setSlots(List<LiveSlotStatusDto> slots) { this.slots = slots; }
}
