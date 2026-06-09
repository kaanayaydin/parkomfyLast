package com.parkomfy.api;

public class EntrancePlateResultDto {
    private String licensePlate;
    private String normalizedPlate;
    private String vehicleId;
    private double confidence;
    private boolean hasReservation;
    private String reservedSlotId;
    private String reservationId;
    private String areaId;

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getNormalizedPlate() { return normalizedPlate; }
    public void setNormalizedPlate(String normalizedPlate) { this.normalizedPlate = normalizedPlate; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isHasReservation() { return hasReservation; }
    public void setHasReservation(boolean hasReservation) { this.hasReservation = hasReservation; }
    public String getReservedSlotId() { return reservedSlotId; }
    public void setReservedSlotId(String reservedSlotId) { this.reservedSlotId = reservedSlotId; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
}
