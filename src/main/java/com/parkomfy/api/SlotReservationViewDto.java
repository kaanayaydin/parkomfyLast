package com.parkomfy.api;

import java.util.ArrayList;
import java.util.List;

public class SlotReservationViewDto {
    private String slotId;
    private int slotNumber;
    private boolean physicallyOccupiedNow;
    private boolean bookableForRange;
    private String reservationStatus;
    private String displayLabel;
    private String blockReason;
    private List<ReservationBlockDto> reservationsInTimeline = new ArrayList<>();
    private List<TimelineSegmentDto> timeline = new ArrayList<>();

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public int getSlotNumber() { return slotNumber; }
    public void setSlotNumber(int slotNumber) { this.slotNumber = slotNumber; }
    public boolean isPhysicallyOccupiedNow() { return physicallyOccupiedNow; }
    public void setPhysicallyOccupiedNow(boolean physicallyOccupiedNow) { this.physicallyOccupiedNow = physicallyOccupiedNow; }
    public boolean isBookableForRange() { return bookableForRange; }
    public void setBookableForRange(boolean bookableForRange) { this.bookableForRange = bookableForRange; }
    public String getReservationStatus() { return reservationStatus; }
    public void setReservationStatus(String reservationStatus) { this.reservationStatus = reservationStatus; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    public List<ReservationBlockDto> getReservationsInTimeline() { return reservationsInTimeline; }
    public void setReservationsInTimeline(List<ReservationBlockDto> reservationsInTimeline) { this.reservationsInTimeline = reservationsInTimeline; }
    public List<TimelineSegmentDto> getTimeline() { return timeline; }
    public void setTimeline(List<TimelineSegmentDto> timeline) { this.timeline = timeline; }
}
