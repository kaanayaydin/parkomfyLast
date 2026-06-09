package com.parkomfy.service;

import com.parkomfy.api.LiveParkingStatusDto;
import com.parkomfy.api.LiveSlotStatusDto;
import com.parkomfy.api.SlotCornerDto;
import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges live CV/DB occupancy with time-range reservations.
 */
public class LiveParkingService {

    private final IParkingRepository repository;

    public LiveParkingService(IParkingRepository repository) {
        this.repository = repository;
    }

    public LiveParkingStatusDto getLiveStatus(String areaId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        ParkingArea area = repository.getArea(areaId);
        if (area == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = rangeStart != null ? rangeStart : now;
        LocalDateTime end = rangeEnd != null ? rangeEnd : now.plusHours(2);

        LiveParkingStatusDto dto = new LiveParkingStatusDto();
        dto.setAreaId(area.getAreaId());
        dto.setAreaName(area.getAreaName());
        dto.setLastUpdated(now);

        int available = 0;
        int occupied = 0;
        int reserved = 0;
        List<LiveSlotStatusDto> slotDtos = new ArrayList<>();

        for (ParkingSlot slot : area.getParkingSlots()) {
            LiveSlotStatusDto s = buildSlotStatus(slot, areaId, start, end, now);
            slotDtos.add(s);
            switch (s.getMergedStatus()) {
                case "AVAILABLE": available++; break;
                case "OCCUPIED": occupied++; break;
                case "RESERVED": reserved++; break;
                default: break;
            }
        }

        dto.setSlots(slotDtos);
        dto.setTotalSlots(slotDtos.size());
        dto.setAvailableSlots(available);
        dto.setOccupiedSlots(occupied);
        dto.setReservedSlots(reserved);
        dto.setOccupancyRate(slotDtos.isEmpty() ? 0 : (double) occupied / slotDtos.size());
        return dto;
    }

    public boolean isSlotBookable(String slotId, LocalDateTime start, LocalDateTime end) {
        ParkingSlot slot = repository.getSlot(slotId);
        if (slot == null) return false;
        if (slot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED
            || slot.getStatus() == ParkingSlot.SlotStatus.MAINTENANCE) {
            return false;
        }
        if (repository.getActiveSessionForSlot(slotId) != null) {
            return false;
        }
        return repository.getOverlappingReservations(slotId, start, end).isEmpty();
    }

    private LiveSlotStatusDto buildSlotStatus(ParkingSlot slot, String areaId,
                                              LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                              LocalDateTime now) {
        LiveSlotStatusDto s = new LiveSlotStatusDto();
        s.setSlotId(slot.getSlotId());
        s.setSlotNumber(slot.getSlotNumber());
        s.setZone(slot.getZone());
        s.setDbStatus(slot.getStatus().name());

        ParkingSession activeSession = repository.getActiveSessionForSlot(slot.getSlotId());
        List<SlotReservation> overlaps = repository.getOverlappingReservations(
            slot.getSlotId(), rangeStart, rangeEnd);

        String merged = "AVAILABLE";
        boolean bookable = true;
        String plate = null;
        String reservationId = null;

        if (slot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED || activeSession != null) {
            merged = "OCCUPIED";
            bookable = false;
            if (activeSession != null && activeSession.getVehicle() != null
                && activeSession.getVehicle().getLicensePlate() != null) {
                plate = activeSession.getVehicle().getLicensePlate().getPlateNumber();
            } else if (slot.getCurrentVehicle() != null && slot.getCurrentVehicle().getLicensePlate() != null) {
                plate = slot.getCurrentVehicle().getLicensePlate().getPlateNumber();
            }
        } else if (!overlaps.isEmpty()) {
            SlotReservation res = overlaps.get(0);
            merged = "RESERVED";
            bookable = false;
            plate = res.getLicensePlate();
            reservationId = res.getReservationId();
        } else if (slot.getStatus() == ParkingSlot.SlotStatus.MAINTENANCE) {
            merged = "MAINTENANCE";
            bookable = false;
        }

        s.setMergedStatus(merged);
        s.setDisplayLabel(toDisplayLabel(merged));
        s.setAvailableForBooking(bookable && isSlotBookable(slot.getSlotId(), rangeStart, rangeEnd));
        s.setLicensePlate(plate);
        s.setReservationId(reservationId);
        if (slot.hasCalibratedCorners()) {
            s.getCorners().add(new SlotCornerDto(slot.getC1x(), slot.getC1y()));
            s.getCorners().add(new SlotCornerDto(slot.getC2x(), slot.getC2y()));
            s.getCorners().add(new SlotCornerDto(slot.getC3x(), slot.getC3y()));
            s.getCorners().add(new SlotCornerDto(slot.getC4x(), slot.getC4y()));
        }
        return s;
    }

    private static String toDisplayLabel(String merged) {
        if ("OCCUPIED".equals(merged)) return "DOLU";
        if ("RESERVED".equals(merged)) return "DOLU (rezerve)";
        if ("MAINTENANCE".equals(merged)) return "BAKIM";
        return "BOŞ";
    }

    public List<ParkingSlot> getAvailableSlotsForRange(String areaId, LocalDateTime start, LocalDateTime end) {
        ParkingArea area = repository.getArea(areaId);
        if (area == null) return new ArrayList<>();
        List<ParkingSlot> result = new ArrayList<>();
        for (ParkingSlot slot : area.getParkingSlots()) {
            if (isSlotBookable(slot.getSlotId(), start, end)) {
                result.add(slot);
            }
        }
        return result;
    }
}
