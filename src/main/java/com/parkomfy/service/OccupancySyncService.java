package com.parkomfy.service;

import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.api.LiveParkingStatusDto;
import com.parkomfy.api.LiveSlotStatusDto;
import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.util.FrameCropUtil;
import com.parkomfy.util.PlateMatcher;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hibrit doluluk: kalibre poligon + yolov8n araç merkezi (gRPC/Python).
 * Her otopark kendi video stream'inden (loop1/2/3) eşzamanlı okunur.
 * Plaka: slot yeni dolunca araç kırpımı OCR.
 */
public class OccupancySyncService {

    private static final double MIN_OCC_CONF = 0.25;

    private final IParkingRepository repository;
    private final IYOLOInference yoloInference;
    private final CameraSimulationService cameraSimulationService;
    private final LiveParkingService liveParkingService;
    private final ParkingEventBroadcaster broadcaster;
    private final PlateSimulationService plateSimulationService;

    public OccupancySyncService(IParkingRepository repository,
                                IYOLOInference yoloInference,
                                CameraSimulationService cameraSimulationService,
                                LiveParkingService liveParkingService,
                                ParkingEventBroadcaster broadcaster,
                                PlateSimulationService plateSimulationService) {
        this.repository = repository;
        this.yoloInference = yoloInference;
        this.cameraSimulationService = cameraSimulationService;
        this.liveParkingService = liveParkingService;
        this.broadcaster = broadcaster;
        this.plateSimulationService = plateSimulationService;
    }

    /**
     * Admin / video overlay: anlık kare + hibrit tespit (CMD ile aynı mantık, DB gecikmesi yok).
     */
    public LiveParkingStatusDto getLiveHybridStatus(String areaId) {
        return getLiveHybridStatus(areaId, null, null);
    }

    /**
     * Mobil rezervasyon + canlı doluluk: hibrit CV + seçilen saat aralığındaki rezervasyonlar.
     */
    public LiveParkingStatusDto getLiveHybridStatus(String areaId,
                                                    LocalDateTime rangeStart,
                                                    LocalDateTime rangeEnd) {
        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        LocalDateTime end = rangeEnd != null ? rangeEnd : start.plusHours(2);

        if (!repository.isAreaCalibrated(areaId)) {
            return liveParkingService.getLiveStatus(areaId, start, end);
        }

        String lotKey = repository.getLotKey(areaId);
        if (lotKey == null || lotKey.isBlank()) {
            lotKey = "loop1";
        }
        byte[] frame = cameraSimulationService.getLiveSnapshotForLot(lotKey);
        LiveParkingStatusDto dto = liveParkingService.getLiveStatus(areaId, start, end);
        if (dto == null || frame == null || frame.length == 0) {
            return dto;
        }

        List<ParkingSlotResultDto> detected;
        try {
            detected = yoloInference.detectParkingSlots(frame, areaId);
        } catch (Exception e) {
            return dto;
        }

        Map<Integer, ParkingSlotResultDto> bySlotNumber = new HashMap<>();
        for (ParkingSlotResultDto det : detected) {
            if (det.getSlotNumber() > 0) {
                bySlotNumber.put(det.getSlotNumber(), det);
            }
        }

        int available = 0;
        int occupied = 0;
        int reserved = 0;
        for (LiveSlotStatusDto s : dto.getSlots()) {
            String prior = s.getMergedStatus();
            ParkingSlotResultDto det = bySlotNumber.get(s.getSlotNumber());
            boolean physical = det != null
                && det.isOccupied()
                && det.getConfidence() >= MIN_OCC_CONF;
            s.setDetectionConfidence(det != null ? det.getConfidence() : 0.0);

            if (physical) {
                s.setMergedStatus("OCCUPIED");
                s.setDisplayLabel("DOLU");
                s.setAvailableForBooking(false);
                occupied++;
            } else if ("RESERVED".equals(prior)) {
                s.setMergedStatus("RESERVED");
                s.setDisplayLabel("DOLU (rezerve)");
                s.setAvailableForBooking(false);
                reserved++;
            } else if ("MAINTENANCE".equals(prior)) {
                s.setAvailableForBooking(false);
                occupied++;
            } else {
                s.setMergedStatus("AVAILABLE");
                s.setDisplayLabel("BOŞ");
                s.setLicensePlate(null);
                s.setReservationId(null);
                s.setAvailableForBooking(liveParkingService.isSlotBookable(s.getSlotId(), start, end));
                available++;
            }
        }

        dto.setAvailableSlots(available);
        dto.setOccupiedSlots(occupied);
        dto.setReservedSlots(reserved);
        dto.setTotalSlots(dto.getSlots().size());
        dto.setOccupancyRate(dto.getSlots().isEmpty() ? 0 : (double) occupied / dto.getSlots().size());
        return dto;
    }

    public void syncAllCalibratedAreas() {
        for (ParkingArea area : repository.getAllAreas()) {
            if (repository.isAreaCalibrated(area.getAreaId())) {
                syncArea(area.getAreaId());
            }
        }
    }

    public void syncArea(String areaId) {
        if (!repository.isAreaCalibrated(areaId)) {
            return;
        }

        String lotKey = repository.getLotKey(areaId);
        if (lotKey == null || lotKey.isBlank()) {
            lotKey = "loop1";
        }

        byte[] frame = cameraSimulationService.getLiveSnapshotForLot(lotKey);
        if (frame == null || frame.length == 0) {
            return;
        }

        ParkingArea area = repository.getArea(areaId);
        if (area == null || area.getParkingSlots().isEmpty()) {
            return;
        }

        List<ParkingSlotResultDto> detected;
        try {
            detected = yoloInference.detectParkingSlots(frame, areaId);
        } catch (Exception e) {
            return;
        }

        Map<Integer, ParkingSlotResultDto> bySlotNumber = new HashMap<>();
        for (ParkingSlotResultDto det : detected) {
            if (det.getSlotNumber() > 0) {
                bySlotNumber.put(det.getSlotNumber(), det);
            }
        }

        List<ParkingSlot> dbSlots = new java.util.ArrayList<>(area.getParkingSlots());
        dbSlots.sort(Comparator.comparingInt(ParkingSlot::getSlotNumber));

        for (ParkingSlot dbSlot : dbSlots) {
            ParkingSlotResultDto det = bySlotNumber.get(dbSlot.getSlotNumber());
            boolean occupied = det != null
                && det.isOccupied()
                && det.getConfidence() >= MIN_OCC_CONF;

            if (occupied) {
                boolean wasEmpty = dbSlot.getStatus() != ParkingSlot.SlotStatus.OCCUPIED;
                if (wasEmpty) {
                    dbSlot.setStatus(ParkingSlot.SlotStatus.OCCUPIED);
                    repository.updateSlot(dbSlot);
                }
                if (repository.getActiveSessionForSlot(dbSlot.getSlotId()) == null) {
                    assignPlateFromHybridDetection(dbSlot, areaId, frame, det);
                }
            } else {
                ParkingSession session = repository.getActiveSessionForSlot(dbSlot.getSlotId());
                if (session != null) {
                    session.setExitTime(java.time.LocalDateTime.now());
                    repository.updateSession(session);
                }
                if (dbSlot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED || session != null) {
                    dbSlot.vacate();
                    repository.updateSlot(dbSlot);
                }
            }
        }

        try {
            byte[] annotated = yoloInference.getParkingSlotsAnnotatedImage(frame, areaId);
            if (annotated != null && annotated.length > 0) {
                broadcaster.cacheAnnotatedImage(areaId, annotated);
            }
        } catch (Exception ignored) {
        }

        LiveParkingStatusDto live = liveParkingService.getLiveStatus(areaId, null, null);
        if (live != null) {
            broadcaster.broadcastLiveStatus(live);
        }
    }

    private void assignPlateFromHybridDetection(ParkingSlot slot, String areaId,
                                                byte[] frame, ParkingSlotResultDto det) {
        String plateText = null;
        if (det != null && det.hasVehicleBBox()) {
            byte[] crop = FrameCropUtil.cropNormalizedJpeg(
                frame,
                det.getVehicleX(),
                det.getVehicleY(),
                det.getVehicleWidth(),
                det.getVehicleHeight()
            );
            if (crop != null) {
                plateText = yoloInference.detectLicensePlateFromCrop(crop, slot.getSlotNumber());
            }
        }

        if (isValidPlate(plateText)) {
            String formatted = formatPlateForDisplay(plateText);
            Vehicle vehicle = plateSimulationService.getOrCreateVehicle(formatted);
            if (slot.isAvailable()) {
                slot.occupy(vehicle);
            } else {
                slot.setCurrentVehicle(vehicle);
            }
            ParkingSession session = new ParkingSession(vehicle, slot);
            repository.saveSession(session);
            repository.updateSlot(slot);
            return;
        }

        plateSimulationService.assignPlateToOccupiedSlot(slot, areaId);
    }

    private static boolean isValidPlate(String plateText) {
        if (plateText == null || plateText.isBlank()) {
            return false;
        }
        String u = plateText.toUpperCase().trim();
        return !u.equals("TESPIT EDILEMEDI")
            && !u.equals("YABANCI")
            && u.length() >= 5;
    }

    private static String formatPlateForDisplay(String raw) {
        String norm = PlateMatcher.normalize(raw);
        if (norm.length() >= 7 && norm.startsWith("34")) {
            return "34 " + norm.substring(2, 5) + " " + norm.substring(5);
        }
        if (norm.length() >= 7 && norm.startsWith("67")) {
            return "67 " + norm.substring(2, 5) + " " + norm.substring(5);
        }
        return raw.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
