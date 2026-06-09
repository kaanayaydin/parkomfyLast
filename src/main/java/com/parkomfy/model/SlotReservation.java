package com.parkomfy.model;

import java.time.LocalDateTime;

/**
 * Time-bounded parking reservation for a specific slot.
 */
public class SlotReservation {
    private String reservationId;
    private String slotId;
    private String areaId;
    private String licensePlate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReservationStatus status;
    private double totalFee;
    private LocalDateTime createdAt;

    public SlotReservation(String slotId, String areaId, String licensePlate,
                           LocalDateTime startTime, LocalDateTime endTime, double totalFee) {
        this.reservationId = "RES-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
        this.slotId = slotId;
        this.areaId = areaId;
        this.licensePlate = licensePlate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalFee = totalFee;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = LocalDateTime.now();
    }

    public boolean overlaps(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        return startTime.isBefore(rangeEnd) && endTime.isAfter(rangeStart);
    }

    public boolean isActiveAt(LocalDateTime instant) {
        return (status == ReservationStatus.RESERVED || status == ReservationStatus.ACTIVE)
            && !instant.isBefore(startTime)
            && instant.isBefore(endTime);
    }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public double getTotalFee() { return totalFee; }
    public void setTotalFee(double totalFee) { this.totalFee = totalFee; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public enum ReservationStatus {
        RESERVED,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
}
