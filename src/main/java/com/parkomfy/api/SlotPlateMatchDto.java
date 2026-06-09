package com.parkomfy.api;

public class SlotPlateMatchDto {
    private String slotId;
    private String detectedPlate;
    private String matchedPlate;
    private double matchScore;
    private double detectionConfidence;
    private boolean wrongSlot;
    private String expectedSlotId;
    private String sessionId;

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getDetectedPlate() { return detectedPlate; }
    public void setDetectedPlate(String detectedPlate) { this.detectedPlate = detectedPlate; }
    public String getMatchedPlate() { return matchedPlate; }
    public void setMatchedPlate(String matchedPlate) { this.matchedPlate = matchedPlate; }
    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
    public double getDetectionConfidence() { return detectionConfidence; }
    public void setDetectionConfidence(double detectionConfidence) { this.detectionConfidence = detectionConfidence; }
    public boolean isWrongSlot() { return wrongSlot; }
    public void setWrongSlot(boolean wrongSlot) { this.wrongSlot = wrongSlot; }
    public String getExpectedSlotId() { return expectedSlotId; }
    public void setExpectedSlotId(String expectedSlotId) { this.expectedSlotId = expectedSlotId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
