package com.parkomfy.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Merged slot status: live occupancy + reservations.
 */
public class LiveSlotStatusDto {
    private String slotId;
    private int slotNumber;
    private String zone;
    private String mergedStatus;
    /** UI etiketi: BOŞ / DOLU / DOLU (rezerve) */
    private String displayLabel;
    private String dbStatus;
    private String licensePlate;
    private String reservationId;
    private boolean availableForBooking;
    private double detectionConfidence;
    /** Kalibre poligon köşeleri (0-1 normalize), video overlay için sabit. */
    private List<SlotCornerDto> corners = new ArrayList<>();

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public int getSlotNumber() { return slotNumber; }
    public void setSlotNumber(int slotNumber) { this.slotNumber = slotNumber; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getMergedStatus() { return mergedStatus; }
    public void setMergedStatus(String mergedStatus) { this.mergedStatus = mergedStatus; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public String getDbStatus() { return dbStatus; }
    public void setDbStatus(String dbStatus) { this.dbStatus = dbStatus; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public boolean isAvailableForBooking() { return availableForBooking; }
    public void setAvailableForBooking(boolean availableForBooking) { this.availableForBooking = availableForBooking; }
    public double getDetectionConfidence() { return detectionConfidence; }
    public void setDetectionConfidence(double detectionConfidence) { this.detectionConfidence = detectionConfidence; }
    public List<SlotCornerDto> getCorners() { return corners; }
    public void setCorners(List<SlotCornerDto> corners) { this.corners = corners; }
}
