package com.parkomfy.api;

public class CreateParkingAreaRequest {
    private String areaName;
    private String address;
    private String lotKey;
    private String description;
    private Double hourlyRate;
    private Double firstHourRate;
    private Integer freeMinutes;
    private Double maxDailyRate;

    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLotKey() { return lotKey; }
    public void setLotKey(String lotKey) { this.lotKey = lotKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }
    public Double getFirstHourRate() { return firstHourRate; }
    public void setFirstHourRate(Double firstHourRate) { this.firstHourRate = firstHourRate; }
    public Integer getFreeMinutes() { return freeMinutes; }
    public void setFreeMinutes(Integer freeMinutes) { this.freeMinutes = freeMinutes; }
    public Double getMaxDailyRate() { return maxDailyRate; }
    public void setMaxDailyRate(Double maxDailyRate) { this.maxDailyRate = maxDailyRate; }
}
