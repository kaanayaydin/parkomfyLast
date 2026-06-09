package com.parkomfy.service;

import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.api.LiveParkingStatusDto;
import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Kalibre alanlarda model doluluk + simüle plaka eşleşmesi; 5 sn aralıkla DB günceller.
 */
public class OccupancySyncService {

    private static final double MIN_OCC_CONF = 0.35;

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
        byte[] frame = cameraSimulationService.getLiveSnapshot();
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

        List<ParkingSlot> dbSlots = new java.util.ArrayList<>(area.getParkingSlots());
        dbSlots.sort(Comparator.comparingInt(ParkingSlot::getSlotNumber));

        for (int i = 0; i < dbSlots.size(); i++) {
            ParkingSlot dbSlot = dbSlots.get(i);
            boolean occupied = false;
            if (i < detected.size()) {
                ParkingSlotResultDto det = detected.get(i);
                occupied = det.isOccupied() && det.getConfidence() >= MIN_OCC_CONF;
            }

            if (occupied) {
                if (dbSlot.getStatus() != ParkingSlot.SlotStatus.OCCUPIED) {
                    dbSlot.setStatus(ParkingSlot.SlotStatus.OCCUPIED);
                    repository.updateSlot(dbSlot);
                }
                plateSimulationService.assignPlateToOccupiedSlot(dbSlot, areaId);
            } else {
                ParkingSession session = repository.getActiveSessionForSlot(dbSlot.getSlotId());
                if (session == null && dbSlot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED) {
                    dbSlot.vacate();
                    repository.updateSlot(dbSlot);
                }
            }
        }

        try {
            byte[] annotated = yoloInference.getParkingSlotsAnnotatedImage(frame);
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
}
