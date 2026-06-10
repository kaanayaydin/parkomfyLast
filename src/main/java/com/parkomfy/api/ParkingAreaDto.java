package com.parkomfy.api;

public class ParkingAreaDto {
    private String areaId;
    private String areaName;
    private String address;
    private String lotKey;
    private boolean calibrated;
    private int slotCount;
    private String description;
    private double hourlyRate;
    private Double firstHourRate;
    private int freeMinutes;
    private Double maxDailyRate;

    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLotKey() { return lotKey; }
    public void setLotKey(String lotKey) { this.lotKey = lotKey; }
    public boolean isCalibrated() { return calibrated; }
    public void setCalibrated(boolean calibrated) { this.calibrated = calibrated; }
    public int getSlotCount() { return slotCount; }
    public void setSlotCount(int slotCount) { this.slotCount = slotCount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) { this.hourlyRate = hourlyRate; }
    public Double getFirstHourRate() { return firstHourRate; }
    public void setFirstHourRate(Double firstHourRate) { this.firstHourRate = firstHourRate; }
    public int getFreeMinutes() { return freeMinutes; }
    public void setFreeMinutes(int freeMinutes) { this.freeMinutes = freeMinutes; }
    public Double getMaxDailyRate() { return maxDailyRate; }
    public void setMaxDailyRate(Double maxDailyRate) { this.maxDailyRate = maxDailyRate; }
}
