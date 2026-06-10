package com.parkomfy.service;

import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;

import com.parkomfy.util.TimeRangeUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Time-range based slot reservation logic.
 */
public class ReservationService {

    private final IParkingRepository repository;
    private LiveParkingService liveParkingService;
    private ReservationAvailabilityService availabilityService;

    public ReservationService(IParkingRepository repository) {
        this.repository = repository;
    }

    public void setLiveParkingService(LiveParkingService liveParkingService) {
        this.liveParkingService = liveParkingService;
    }

    public void setAvailabilityService(ReservationAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    public List<ParkingSlot> findAvailableSlotsForRange(String areaId, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        if (liveParkingService != null) {
            return liveParkingService.getAvailableSlotsForRange(areaId, start, end);
        }
        ParkingArea area = repository.getArea(areaId);
        if (area == null) {
            return new ArrayList<>();
        }
        return area.getParkingSlots().stream()
            .filter(slot -> isSlotAvailableForRange(slot.getSlotId(), start, end))
            .collect(Collectors.toList());
    }

    public boolean isSlotAvailableForRange(String slotId, LocalDateTime start, LocalDateTime end) {
        if (liveParkingService != null) {
            return liveParkingService.isSlotBookable(slotId, start, end);
        }
        ParkingSlot slot = repository.getSlot(slotId);
        if (slot != null && (slot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED
            || slot.getStatus() == ParkingSlot.SlotStatus.MAINTENANCE)) {
            return false;
        }
        if (repository.getActiveSessionForSlot(slotId) != null) {
            return false;
        }
        return repository.getOverlappingReservations(slotId, start, end).isEmpty();
    }

    public SlotReservation createReservation(String areaId, String slotId, String licensePlate,
                                             LocalDateTime start, LocalDateTime end, PricingPolicy policy) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Bitiş zamanı başlangıçtan sonra olmalıdır");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Başlangıç zamanı geçmişte olamaz");
        }
        long hours = Duration.between(start, end).toHours();
        if (hours > 24) {
            throw new IllegalArgumentException("Rezervasyon süresi en fazla 24 saat olabilir");
        }
        ParkingArea area = repository.getArea(areaId);
        if (area == null) {
            throw new IllegalArgumentException("Parking area not found: " + areaId);
        }
        ParkingSlot slot = area.getSlotById(slotId);
        if (slot == null) {
            slot = repository.getSlot(slotId);
        }
        if (slot == null) {
            throw new IllegalArgumentException("Slot not found: " + slotId);
        }
        if (availabilityService != null) {
            com.parkomfy.api.AreaReservationViewDto view = availabilityService.getReservationView(
                areaId, start, end, null, null);
            com.parkomfy.api.SlotReservationViewDto slotView = view.getSlots().stream()
                .filter(s -> slotId.equals(s.getSlotId()))
                .findFirst()
                .orElse(null);
            if (slotView == null || !slotView.isBookableForRange()) {
                String reason = slotView != null && slotView.getBlockReason() != null
                    ? slotView.getBlockReason()
                    : "Slot seçilen saat aralığında müsait değil";
                throw new IllegalStateException(reason + " (dakika bile çakışma kabul edilmez)");
            }
        } else if (!isSlotAvailableForRange(slotId, start, end)) {
            throw new IllegalStateException("Slot is not available for the selected time range");
        }

        long minutes = Duration.between(start, end).toMinutes();
        PricingPolicy pricing = policy != null ? policy : new PricingPolicy("DEFAULT", 20.0);
        double totalFee = pricing.calculateFee(minutes);

        SlotReservation reservation = new SlotReservation(slotId, areaId, licensePlate, start, end, totalFee);
        repository.saveReservation(reservation);
        return reservation;
    }

    public List<SlotReservation> getReservationsByPlate(String licensePlate) {
        return repository.getReservationsByPlate(licensePlate);
    }

    public SlotReservation cancelReservation(String reservationId, String licensePlate) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId is required");
        }
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("licensePlate is required");
        }
        SlotReservation reservation = repository.getReservation(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation not found");
        }
        if (!reservation.getLicensePlate().equalsIgnoreCase(licensePlate.trim())) {
            throw new IllegalArgumentException("Reservation does not belong to this license plate");
        }
        if (reservation.getStatus() != SlotReservation.ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only active reservations can be cancelled");
        }
        if (!reservation.getEndTime().isAfter(java.time.LocalDateTime.now())) {
            throw new IllegalStateException("Past reservations cannot be cancelled");
        }
        reservation.setStatus(SlotReservation.ReservationStatus.CANCELLED);
        repository.updateReservation(reservation);
        return reservation;
    }
}
