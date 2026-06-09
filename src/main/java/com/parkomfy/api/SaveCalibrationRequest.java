package com.parkomfy.api;

import java.util.ArrayList;
import java.util.List;

public class SaveCalibrationRequest {
    private String areaId;
    private String lotKey;
    private int imageWidth;
    private int imageHeight;
    private List<CalibratedSlotDto> slots = new ArrayList<>();

    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getLotKey() { return lotKey; }
    public void setLotKey(String lotKey) { this.lotKey = lotKey; }
    public int getImageWidth() { return imageWidth; }
    public void setImageWidth(int imageWidth) { this.imageWidth = imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }
    public List<CalibratedSlotDto> getSlots() { return slots; }
    public void setSlots(List<CalibratedSlotDto> slots) { this.slots = slots; }

    public static class CalibratedSlotDto {
        private int slotNumber;
        private List<SlotCornerDto> corners = new ArrayList<>();
        private boolean occupied;

        public int getSlotNumber() { return slotNumber; }
        public void setSlotNumber(int slotNumber) { this.slotNumber = slotNumber; }
        public List<SlotCornerDto> getCorners() { return corners; }
        public void setCorners(List<SlotCornerDto> corners) { this.corners = corners; }
        public boolean isOccupied() { return occupied; }
        public void setOccupied(boolean occupied) { this.occupied = occupied; }
    }
}
