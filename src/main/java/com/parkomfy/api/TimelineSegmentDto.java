package com.parkomfy.api;

/**
 * One segment on the reservation timeline (e.g. 1 hour).
 * status: FREE | RESERVED | LIVE_OCCUPIED
 */
public class TimelineSegmentDto {
    private String startTime;
    private String endTime;
    private String status;
    private String label;

    public TimelineSegmentDto() {}

    public TimelineSegmentDto(String startTime, String endTime, String status, String label) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.label = label;
    }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
