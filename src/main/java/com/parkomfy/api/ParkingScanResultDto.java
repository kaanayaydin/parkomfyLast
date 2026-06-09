package com.parkomfy.api;

import java.util.ArrayList;
import java.util.List;

public class ParkingScanResultDto {
    private String areaId;
    private int slotsScanned;
    private int matchesFound;
    private List<SlotPlateMatchDto> matches = new ArrayList<>();

    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public int getSlotsScanned() { return slotsScanned; }
    public void setSlotsScanned(int slotsScanned) { this.slotsScanned = slotsScanned; }
    public int getMatchesFound() { return matchesFound; }
    public void setMatchesFound(int matchesFound) { this.matchesFound = matchesFound; }
    public List<SlotPlateMatchDto> getMatches() { return matches; }
    public void setMatches(List<SlotPlateMatchDto> matches) { this.matches = matches; }
}
