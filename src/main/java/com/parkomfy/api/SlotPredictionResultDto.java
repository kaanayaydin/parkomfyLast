package com.parkomfy.api;

import java.util.ArrayList;
import java.util.List;

/** Model tahmini + referans kare (mobilde aynı görüntü üzerinde poligon çizimi). */
public class SlotPredictionResultDto {
    private List<PredictedSlotDto> slots = new ArrayList<>();
    private String imageBase64;
    private int imageWidth;
    private int imageHeight;

    public List<PredictedSlotDto> getSlots() { return slots; }
    public void setSlots(List<PredictedSlotDto> slots) { this.slots = slots; }
    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
    public int getImageWidth() { return imageWidth; }
    public void setImageWidth(int imageWidth) { this.imageWidth = imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }
}
