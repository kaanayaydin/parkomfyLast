package com.parkomfy.api;

import com.parkomfy.model.ParkingSession;
import com.parkomfy.model.Payment;

import java.time.LocalDateTime;

public class PaymentHistoryDto {
    private String paymentId;
    private String sessionId;
    private double amount;
    private String currency;
    private String status;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private long durationSeconds;
    private String slotId;
    private String areaId;
    private String areaName;
    private String licensePlate;
    private LocalDateTime createdAt;

    public PaymentHistoryDto() {}

    public static PaymentHistoryDto from(Payment payment, String areaName, String licensePlate) {
        PaymentHistoryDto dto = new PaymentHistoryDto();
        dto.paymentId = payment.getPaymentId();
        dto.amount = payment.getAmount();
        dto.currency = "TRY";
        dto.status = payment.getStatus().name();
        dto.createdAt = payment.getPaymentTime();
        dto.areaName = areaName;
        dto.licensePlate = licensePlate;

        if (payment.getStoredLicensePlate() != null) {
            dto.licensePlate = payment.getStoredLicensePlate();
        }
        if (payment.getStoredSlotId() != null) {
            dto.slotId = payment.getStoredSlotId();
        }
        if (payment.getStoredAreaId() != null) {
            dto.areaId = payment.getStoredAreaId();
        }
        if (payment.getStoredEntryTime() != null) {
            dto.entryTime = payment.getStoredEntryTime();
        }
        if (payment.getStoredExitTime() != null) {
            dto.exitTime = payment.getStoredExitTime();
        }
        if (payment.getStoredDurationSeconds() > 0) {
            dto.durationSeconds = payment.getStoredDurationSeconds();
        }

        ParkingSession session = payment.getParkingSession();
        if (session != null) {
            if (dto.sessionId == null) {
                dto.sessionId = session.getSessionId();
            }
            if (dto.entryTime == null) {
                dto.entryTime = session.getEntryTime();
            }
            if (dto.exitTime == null) {
                dto.exitTime = session.getExitTime();
            }
            if (dto.slotId == null && session.getParkingSlot() != null) {
                dto.slotId = session.getParkingSlot().getSlotId();
            }
            if (dto.licensePlate == null && session.getVehicle() != null
                    && session.getVehicle().getLicensePlate() != null) {
                dto.licensePlate = session.getVehicle().getLicensePlate().getPlateNumber();
            }
            if (dto.durationSeconds <= 0) {
                if (dto.entryTime != null && dto.exitTime != null) {
                    dto.durationSeconds = Math.max(0,
                        java.time.Duration.between(dto.entryTime, dto.exitTime).getSeconds());
                } else if (session.getEntryTime() != null && session.getDurationMinutes() > 0) {
                    dto.durationSeconds = session.getDurationMinutes() * 60;
                }
            }
        }
        return dto;
    }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
