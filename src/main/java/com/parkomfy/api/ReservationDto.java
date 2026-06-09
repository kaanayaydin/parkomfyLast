package com.parkomfy.api;

import com.parkomfy.model.SlotReservation;

/**
 * DTO for slot reservation responses.
 */
public class ReservationDto {
    private String reservationId;
    private String slotId;
    private String areaId;
    private String licensePlate;
    private String startTime;
    private String endTime;
    private String status;
    private double totalFee;

    public ReservationDto() {}

    public ReservationDto(SlotReservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.slotId = reservation.getSlotId();
        this.areaId = reservation.getAreaId();
        this.licensePlate = reservation.getLicensePlate();
        this.startTime = reservation.getStartTime().toString();
        this.endTime = reservation.getEndTime().toString();
        this.status = reservation.getStatus().name();
        this.totalFee = reservation.getTotalFee();
    }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getTotalFee() { return totalFee; }
    public void setTotalFee(double totalFee) { this.totalFee = totalFee; }
}
