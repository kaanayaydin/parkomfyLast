package com.parkomfy.api;

public class ParkingAreaDto {
    private String areaId;
    private String areaName;
    private String address;
    private String lotKey;
    private boolean calibrated;
    private int slotCount;

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
}
