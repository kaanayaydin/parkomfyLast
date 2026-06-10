package com.parkomfy.service;

import com.parkomfy.api.*;
import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.util.TimeRangeUtil;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Rezervasyon görünümü: canlı doluluk ve rezervasyon çakışması ayrı değerlendirilir.
 * Çakışmada sıfır tolerans (dakika bile örtüşme yok).
 */
public class ReservationAvailabilityService {

    private final IParkingRepository repository;
    private final OccupancySyncService occupancySyncService;

    public ReservationAvailabilityService(IParkingRepository repository,
                                          OccupancySyncService occupancySyncService) {
        this.repository = repository;
        this.occupancySyncService = occupancySyncService;
    }

    public AreaReservationViewDto getReservationView(String areaId,
                                                     LocalDateTime rangeStart,
                                                     LocalDateTime rangeEnd,
                                                     LocalDateTime timelineStart,
                                                     LocalDateTime timelineEnd) {
        if (rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        ParkingArea area = repository.getArea(areaId);
        if (area == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tlStart = timelineStart != null ? timelineStart : rangeStart.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime tlEnd = timelineEnd != null ? timelineEnd : rangeEnd.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        if (!tlEnd.isAfter(tlStart)) {
            tlEnd = tlStart.plusHours(6);
        }

        Map<Integer, Boolean> physicalByNumber = occupancySyncService.getPhysicalOccupancyBySlotNumber(areaId);

        AreaReservationViewDto dto = new AreaReservationViewDto();
        dto.setAreaId(area.getAreaId());
        dto.setAreaName(area.getAreaName());
        dto.setRangeStart(rangeStart.toString());
        dto.setRangeEnd(rangeEnd.toString());
        dto.setTimelineStart(tlStart.toString());
        dto.setTimelineEnd(tlEnd.toString());

        List<SlotReservationViewDto> slotViews = new ArrayList<>();
        for (ParkingSlot slot : area.getParkingSlots()) {
            slotViews.add(buildSlotView(slot, rangeStart, rangeEnd, tlStart, tlEnd, now, physicalByNumber));
        }
        dto.setSlots(slotViews);
        return dto;
    }

    public Optional<SlotReservation> findConflictingReservation(String slotId,
                                                                LocalDateTime rangeStart,
                                                                LocalDateTime rangeEnd) {
        for (SlotReservation r : repository.getOverlappingReservations(slotId, rangeStart, rangeEnd)) {
            if (r.getStatus() == SlotReservation.ReservationStatus.RESERVED
                || r.getStatus() == SlotReservation.ReservationStatus.ACTIVE) {
                if (TimeRangeUtil.overlaps(r.getStartTime(), r.getEndTime(), rangeStart, rangeEnd)) {
                    return Optional.of(r);
                }
            }
        }
        return Optional.empty();
    }

    public boolean isStrictlyBookable(String slotId, LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                      boolean physicallyOccupiedNow, LocalDateTime now) {
        ParkingSlot slot = repository.getSlot(slotId);
        if (slot == null) return false;
        if (slot.getStatus() == ParkingSlot.SlotStatus.MAINTENANCE) return false;
        if (physicallyOccupiedNow) {
            return false;
        }
        if (repository.getActiveSessionForSlot(slotId) != null) {
            return false;
        }
        return findConflictingReservation(slotId, rangeStart, rangeEnd).isEmpty();
    }

    private SlotReservationViewDto buildSlotView(ParkingSlot slot,
                                                 LocalDateTime rangeStart,
                                                 LocalDateTime rangeEnd,
                                                 LocalDateTime tlStart,
                                                 LocalDateTime tlEnd,
                                                 LocalDateTime now,
                                                 Map<Integer, Boolean> physicalByNumber) {
        boolean physical = Boolean.TRUE.equals(physicalByNumber.get(slot.getSlotNumber()))
            || slot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED
            || repository.getActiveSessionForSlot(slot.getSlotId()) != null;

        SlotReservationViewDto view = new SlotReservationViewDto();
        view.setSlotId(slot.getSlotId());
        view.setSlotNumber(slot.getSlotNumber());
        view.setPhysicallyOccupiedNow(physical);

        List<SlotReservation> timelineReservations = repository.getOverlappingReservations(
            slot.getSlotId(), tlStart, tlEnd);
        List<ReservationBlockDto> blocks = new ArrayList<>();
        for (SlotReservation r : timelineReservations) {
            if (r.getStatus() != SlotReservation.ReservationStatus.RESERVED
                && r.getStatus() != SlotReservation.ReservationStatus.ACTIVE) {
                continue;
            }
            ReservationBlockDto b = new ReservationBlockDto();
            b.setReservationId(r.getReservationId());
            b.setLicensePlate(r.getLicensePlate());
            b.setStartTime(r.getStartTime().toString());
            b.setEndTime(r.getEndTime().toString());
            b.setStatus(r.getStatus().name());
            blocks.add(b);
        }
        view.setReservationsInTimeline(blocks);
        view.setTimeline(buildTimeline(tlStart, tlEnd, now, physical, timelineReservations));

        Optional<SlotReservation> conflict = findConflictingReservation(
            slot.getSlotId(), rangeStart, rangeEnd);
        boolean bookable = isStrictlyBookable(slot.getSlotId(), rangeStart, rangeEnd, physical, now);
        view.setBookableForRange(bookable);

        if (physical && conflict.isPresent()) {
            SlotReservation c = conflict.get();
            view.setReservationStatus("LIVE_AND_RESERVED");
            view.setDisplayLabel("CANLI DOLU + REZERVE");
            view.setBlockReason(String.format(
                "Şu an canlı dolu. Mevcut rezervasyon: %s – %s (%s)",
                c.getStartTime(), c.getEndTime(), c.getLicensePlate()));
        } else if (physical) {
            view.setReservationStatus("LIVE_OCCUPIED");
            view.setDisplayLabel("CANLI DOLU");
            view.setBlockReason("Slot şu an canlı dolu — boşalana kadar rezervasyon yapılamaz");
        } else if (conflict.isPresent()) {
            view.setReservationStatus("RESERVED_CONFLICT");
            view.setDisplayLabel("REZERVE (çakışma)");
            SlotReservation c = conflict.get();
            view.setBlockReason(String.format(
                "Rezervasyon çakışması: %s – %s (%s)",
                c.getStartTime(), c.getEndTime(), c.getLicensePlate()));
        } else if (slot.getStatus() == ParkingSlot.SlotStatus.MAINTENANCE) {
            view.setReservationStatus("MAINTENANCE");
            view.setDisplayLabel("BAKIM");
            view.setBlockReason("Slot bakımda");
        } else {
            view.setReservationStatus("AVAILABLE");
            view.setDisplayLabel("MÜSAİT");
            view.setBlockReason(null);
        }

        return view;
    }

    private List<TimelineSegmentDto> buildTimeline(LocalDateTime tlStart,
                                                   LocalDateTime tlEnd,
                                                   LocalDateTime now,
                                                   boolean physicallyOccupiedNow,
                                                   List<SlotReservation> reservations) {
        List<TimelineSegmentDto> segments = new ArrayList<>();
        LocalDateTime cursor = tlStart.truncatedTo(ChronoUnit.HOURS);
        while (cursor.isBefore(tlEnd)) {
            LocalDateTime segEnd = cursor.plusHours(1);
            boolean reservedHere = false;
            String reservedPlate = null;
            for (SlotReservation r : reservations) {
                if (r.getStatus() != SlotReservation.ReservationStatus.RESERVED
                    && r.getStatus() != SlotReservation.ReservationStatus.ACTIVE) {
                    continue;
                }
                if (TimeRangeUtil.overlaps(r.getStartTime(), r.getEndTime(), cursor, segEnd)) {
                    reservedHere = true;
                    reservedPlate = r.getLicensePlate();
                    break;
                }
            }

            boolean liveHere = physicallyOccupiedNow
                && !now.isBefore(cursor) && now.isBefore(segEnd);

            String status = "FREE";
            String label = "Boş";
            if (reservedHere && liveHere) {
                status = "LIVE_RESERVED";
                label = reservedPlate != null ? "Rezerve+" + reservedPlate : "Rezerve+canlı";
            } else if (reservedHere) {
                status = "RESERVED";
                label = reservedPlate != null ? "Rezerve " + reservedPlate : "Rezerve";
            } else if (liveHere) {
                status = "LIVE_OCCUPIED";
                label = "Canlı dolu";
            }

            segments.add(new TimelineSegmentDto(
                cursor.toString(),
                segEnd.toString(),
                status,
                label
            ));
            cursor = segEnd;
        }
        return segments;
    }
}
