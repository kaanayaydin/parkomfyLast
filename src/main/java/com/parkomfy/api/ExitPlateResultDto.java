package com.parkomfy.api;

public class ExitPlateResultDto {
    private String licensePlate;
    private String normalizedPlate;
    private String sessionId;
    private String slotId;
    private double confidence;
    private boolean exited;

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getNormalizedPlate() { return normalizedPlate; }
    public void setNormalizedPlate(String normalizedPlate) { this.normalizedPlate = normalizedPlate; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isExited() { return exited; }
    public void setExited(boolean exited) { this.exited = exited; }
}
