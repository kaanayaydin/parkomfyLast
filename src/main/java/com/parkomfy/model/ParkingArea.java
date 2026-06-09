package com.parkomfy.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ParkingArea class represents a parking facility
 * Contains multiple floors, zones, and parking slots
 */
public class ParkingArea {
    private String areaId;
    private String areaName;
    private String address;
    private List<ParkingSlot> parkingSlots;
    private List<Camera> cameras;
    private PricingPolicy pricingPolicy;
    private int totalCapacity;
    
    public ParkingArea(String areaId, String areaName, String address) {
        this.areaId = areaId;
        this.areaName = areaName;
        this.address = address;
        this.parkingSlots = new ArrayList<>();
        this.cameras = new ArrayList<>();
        this.totalCapacity = 0;
    }
    
    public String getAreaId() {
        return areaId;
    }
    
    public String getAreaName() {
        return areaName;
    }
    
    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public List<ParkingSlot> getParkingSlots() {
        return new ArrayList<>(parkingSlots);
    }
    
    public void addParkingSlot(ParkingSlot slot) {
        if (!parkingSlots.contains(slot)) {
            parkingSlots.add(slot);
            totalCapacity++;
        }
    }
    
    public void removeParkingSlot(ParkingSlot slot) {
        if (parkingSlots.remove(slot)) {
            totalCapacity--;
        }
    }
    
    public ParkingSlot getSlotById(String slotId) {
        return parkingSlots.stream()
                .filter(slot -> slot.getSlotId().equals(slotId))
                .findFirst()
                .orElse(null);
    }
    
    public List<ParkingSlot> getAvailableSlots() {
        return parkingSlots.stream()
                .filter(ParkingSlot::isAvailable)
                .collect(Collectors.toList());
    }
    
    public List<ParkingSlot> getOccupiedSlots() {
        return parkingSlots.stream()
                .filter(ParkingSlot::isOccupied)
                .collect(Collectors.toList());
    }
    
    public int getAvailableSlotCount() {
        return (int) parkingSlots.stream()
                .filter(ParkingSlot::isAvailable)
                .count();
    }
    
    public int getOccupiedSlotCount() {
        return (int) parkingSlots.stream()
                .filter(ParkingSlot::isOccupied)
                .count();
    }
    
    public double getOccupancyRate() {
        if (totalCapacity == 0) return 0.0;
        return (double) getOccupiedSlotCount() / totalCapacity * 100.0;
    }
    
    public List<Camera> getCameras() {
        return new ArrayList<>(cameras);
    }
    
    public void addCamera(Camera camera) {
        if (!cameras.contains(camera)) {
            cameras.add(camera);
        }
    }
    
    public PricingPolicy getPricingPolicy() {
        return pricingPolicy;
    }
    
    public void setPricingPolicy(PricingPolicy pricingPolicy) {
        this.pricingPolicy = pricingPolicy;
    }
    
    public int getTotalCapacity() {
        return totalCapacity;
    }
    
    @Override
    public String toString() {
        return "ParkingArea{" +
                "areaId='" + areaId + '\'' +
                ", areaName='" + areaName + '\'' +
                ", totalCapacity=" + totalCapacity +
                ", availableSlots=" + getAvailableSlotCount() +
                ", occupiedSlots=" + getOccupiedSlotCount() +
                ", occupancyRate=" + String.format("%.2f", getOccupancyRate()) + "%" +
                '}';
    }
}
