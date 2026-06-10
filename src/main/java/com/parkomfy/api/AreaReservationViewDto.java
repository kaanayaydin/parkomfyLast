package com.parkomfy.api;

import java.util.ArrayList;
import java.util.List;

public class AreaReservationViewDto {
    private String areaId;
    private String areaName;
    private String rangeStart;
    private String rangeEnd;
    private String timelineStart;
    private String timelineEnd;
    private List<SlotReservationViewDto> slots = new ArrayList<>();

    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public String getRangeStart() { return rangeStart; }
    public void setRangeStart(String rangeStart) { this.rangeStart = rangeStart; }
    public String getRangeEnd() { return rangeEnd; }
    public void setRangeEnd(String rangeEnd) { this.rangeEnd = rangeEnd; }
    public String getTimelineStart() { return timelineStart; }
    public void setTimelineStart(String timelineStart) { this.timelineStart = timelineStart; }
    public String getTimelineEnd() { return timelineEnd; }
    public void setTimelineEnd(String timelineEnd) { this.timelineEnd = timelineEnd; }
    public List<SlotReservationViewDto> getSlots() { return slots; }
    public void setSlots(List<SlotReservationViewDto> slots) { this.slots = slots; }
}
