package com.parkomfy.api;

public class CreateParkingAreaRequest {
    private String areaName;
    private String address;
    private String lotKey;

    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLotKey() { return lotKey; }
    public void setLotKey(String lotKey) { this.lotKey = lotKey; }
}
