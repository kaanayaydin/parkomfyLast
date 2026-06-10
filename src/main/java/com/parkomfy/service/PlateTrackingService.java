package com.parkomfy.service;

import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.api.EntrancePlateResultDto;
import com.parkomfy.api.ExitPlateResultDto;
import com.parkomfy.api.LiveParkingStatusDto;
import com.parkomfy.api.ParkingScanResultDto;
import com.parkomfy.api.SlotPlateMatchDto;
import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.util.PlateMatcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entrance LPR + parking camera slot-plate matching against DB.
 */
public class PlateTrackingService {

    private static final double PLATE_MATCH_THRESHOLD = PlateMatcher.DEFAULT_MATCH_THRESHOLD;
    private static final double MIN_DETECTION_CONFIDENCE = 0.5;

    private final IParkingRepository repository;
    private final IDetectionService detectionService;
    private final IYOLOInference yoloInference;
    private final LiveParkingService liveParkingService;
    private final ParkingEventBroadcaster broadcaster;
    private final NotificationService notificationService;

    public PlateTrackingService(IParkingRepository repository,
                                IDetectionService detectionService,
                                IYOLOInference yoloInference,
                                LiveParkingService liveParkingService,
                                ParkingEventBroadcaster broadcaster,
                                NotificationService notificationService) {
        this.repository = repository;
        this.detectionService = detectionService;
        this.yoloInference = yoloInference;
        this.liveParkingService = liveParkingService;
        this.broadcaster = broadcaster;
        this.notificationService = notificationService;
    }

    public EntrancePlateResultDto processEntrance(byte[] imageBytes) {
        Camera camera = new Camera("ENTRANCE-1", "Giriş Kamerası", Camera.CameraType.ENTRANCE_LPR, "Entrance");
        camera.setCurrentFrame(imageBytes);
        DetectionResult result = detectionService.detectLicensePlate(camera);

        EntrancePlateResultDto dto = new EntrancePlateResultDto();
        if (result.getLicensePlateText() == null || result.getLicensePlateText().isBlank()) {
            dto.setLicensePlate("");
            dto.setConfidence(0);
            return dto;
        }

        String plate = result.getLicensePlateText().trim();
        String normalized = PlateMatcher.normalize(plate);
        dto.setLicensePlate(plate);
        dto.setNormalizedPlate(normalized);
        dto.setConfidence(result.getConfidence());

        LicensePlate lp = new LicensePlate(normalized);
        Vehicle vehicle = repository.getVehicleByPlate(lp);
        if (vehicle == null) {
            vehicle = new Vehicle(lp);
            repository.saveVehicle(vehicle);
        }
        dto.setVehicleId(vehicle.getVehicleId());

        LocalDateTime now = LocalDateTime.now();
        SlotReservation activeRes = repository.getActiveReservationByPlate(normalized, now);
        if (activeRes != null) {
            dto.setHasReservation(true);
            dto.setReservedSlotId(activeRes.getSlotId());
            dto.setReservationId(activeRes.getReservationId());
            dto.setAreaId(activeRes.getAreaId());
            activeRes.setStatus(SlotReservation.ReservationStatus.ACTIVE);
            repository.updateReservation(activeRes);
        }

        notificationService.sendToPlate(plate,
            "Giriş kaydı",
            activeRes != null
                ? "Hoş geldiniz! Rezervasyonlu slot: " + activeRes.getSlotId()
                : "Hoş geldiniz! Plakanız kaydedildi: " + plate);

        return dto;
    }

    public ParkingScanResultDto processParkingScan(byte[] imageBytes, String areaId) {
        ParkingScanResultDto scanResult = new ParkingScanResultDto();
        scanResult.setAreaId(areaId);

        ParkingArea area = repository.getArea(areaId);
        if (area == null) {
            return scanResult;
        }

        List<ParkingSlot> dbSlots = area.getParkingSlots();
        List<ParkingSlotResultDto> detectedSlots;
        try {
            detectedSlots = yoloInference.detectParkingSlots(imageBytes, areaId);
        } catch (Exception e) {
            detectedSlots = new ArrayList<>();
        }

        byte[] annotated = tryGetAnnotatedImage(imageBytes);
        if (annotated != null) {
            broadcaster.cacheAnnotatedImage(areaId, annotated);
        }

        List<SlotPlateMatchDto> matches = new ArrayList<>();
        int scanned = 0;

        for (int i = 0; i < detectedSlots.size() && i < dbSlots.size(); i++) {
            ParkingSlotResultDto det = detectedSlots.get(i);
            ParkingSlot dbSlot = dbSlots.get(i);
            if (!det.isOccupied() || det.getConfidence() < MIN_DETECTION_CONFIDENCE) {
                if (!det.isOccupied() && dbSlot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED) {
                    handleSlotBecameEmpty(dbSlot);
                }
                continue;
            }
            scanned++;

            String detectedPlate = readPlateFromSlotRegion(imageBytes, det);
            if (detectedPlate == null || detectedPlate.isBlank()) continue;

            Vehicle matchedVehicle = findBestVehicleMatch(detectedPlate);
            if (matchedVehicle == null) continue;

            double score = PlateMatcher.similarity(detectedPlate, matchedVehicle.getLicensePlate().getPlateNumber());
            if (score < PLATE_MATCH_THRESHOLD) continue;

            SlotPlateMatchDto match = new SlotPlateMatchDto();
            match.setSlotId(dbSlot.getSlotId());
            match.setDetectedPlate(detectedPlate);
            match.setMatchedPlate(matchedVehicle.getLicensePlate().getPlateNumber());
            match.setMatchScore(score);
            match.setDetectionConfidence(det.getConfidence());

            SlotReservation res = repository.getActiveReservationByPlate(
                PlateMatcher.normalize(matchedVehicle.getLicensePlate().getPlateNumber()),
                LocalDateTime.now());
            if (res != null && !res.getSlotId().equals(dbSlot.getSlotId())) {
                match.setWrongSlot(true);
                match.setExpectedSlotId(res.getSlotId());
                notificationService.notifyWrongSlot(
                    matchedVehicle.getLicensePlate().getPlateNumber(),
                    res.getSlotId(), dbSlot.getSlotId(), areaId);
            }

            ParkingSession existing = repository.getActiveSessionForSlot(dbSlot.getSlotId());
            if (existing == null) {
                dbSlot.occupy(matchedVehicle);
                ParkingSession session = new ParkingSession(matchedVehicle, dbSlot);
                repository.saveSession(session);
                repository.updateSlot(dbSlot);
                match.setSessionId(session.getSessionId());
                notificationService.notifyVehicleParked(
                    matchedVehicle.getLicensePlate().getPlateNumber(), dbSlot.getSlotId());
            } else {
                match.setSessionId(existing.getSessionId());
            }

            DetectionResult detResult = new DetectionResult("PARKING-CAM", dbSlot.getSlotId(), true, det.getConfidence());
            detResult.setLicensePlateText(detectedPlate);
            detResult.setDetectionType(DetectionResult.DetectionType.LICENSE_PLATE);
            repository.saveDetectionResult(detResult);

            matches.add(match);
        }

        scanResult.setSlotsScanned(scanned);
        scanResult.setMatchesFound(matches.size());
        scanResult.setMatches(matches);

        LiveParkingStatusDto live = liveParkingService.getLiveStatus(areaId, null, null);
        broadcaster.broadcastLiveStatus(live);
        return scanResult;
    }

    private String readPlateFromSlotRegion(byte[] imageBytes, ParkingSlotResultDto slot) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            int imgW = img.getWidth();
            int imgH = img.getHeight();
            int x = (int) (slot.getX() * imgW);
            int y = (int) (slot.getY() * imgH);
            int w = (int) (slot.getWidth() * imgW);
            int h = (int) (slot.getHeight() * imgH);
            x = Math.max(0, Math.min(x, imgW - 1));
            y = Math.max(0, Math.min(y, imgH - 1));
            w = Math.min(w, imgW - x);
            h = Math.min(h, imgH - y);
            if (w <= 0 || h <= 0) return null;

            BufferedImage crop = img.getSubimage(x, y, w, h);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(crop, "jpg", out);
            Camera cam = new Camera("PARKING-CAM", "Park Kamerası", Camera.CameraType.PARKING_AREA, "Parking");
            cam.setCurrentFrame(out.toByteArray());
            String plate = yoloInference.detectLicensePlateBoundingBox(cam);
            if (plate != null && !plate.isBlank() && !plate.toUpperCase().contains("TESPIT")) {
                return PlateMatcher.normalize(plate);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Overhead camera: match OCR against vehicles that entered but are not yet parked.
     */
    private Vehicle findBestVehicleMatch(String detectedPlate) {
        List<Vehicle> enteredNotParked = new ArrayList<>();
        for (Vehicle v : repository.getRecentEnteredVehicles()) {
            if (v.getLicensePlate() == null) continue;
            String norm = PlateMatcher.normalize(v.getLicensePlate().getPlateNumber());
            if (repository.getActiveSessionByPlate(norm) != null) continue;
            enteredNotParked.add(v);
        }
        PlateMatcher.MatchResult<Vehicle> match = PlateMatcher.findBestMatch(
            detectedPlate,
            enteredNotParked,
            v -> v.getLicensePlate().getPlateNumber(),
            PLATE_MATCH_THRESHOLD);
        return match != null ? match.getItem() : null;
    }

    private void handleSlotBecameEmpty(ParkingSlot dbSlot) {
        ParkingSession session = repository.getActiveSessionForSlot(dbSlot.getSlotId());
        if (session != null) {
            session.setStatus(ParkingSession.SessionStatus.LEAVING);
            repository.updateSession(session);
            dbSlot.vacate();
            repository.updateSlot(dbSlot);
            if (session.getVehicle() != null && session.getVehicle().getLicensePlate() != null) {
                notificationService.sendToPlate(
                    session.getVehicle().getLicensePlate().getPlateNumber(),
                    "Çıkış yönünde",
                    "Slot terk edildi — çıkış bariyerine yönlendiriliyor.");
            }
        } else {
            dbSlot.vacate();
            repository.updateSlot(dbSlot);
        }
    }

    public ExitPlateResultDto processExit(byte[] imageBytes) {
        Camera camera = new Camera("EXIT-1", "Çıkış Kamerası", Camera.CameraType.ENTRANCE_LPR, "Exit");
        camera.setCurrentFrame(imageBytes);
        DetectionResult result = detectionService.detectLicensePlate(camera);

        ExitPlateResultDto dto = new ExitPlateResultDto();
        dto.setExited(false);
        if (result.getLicensePlateText() == null || result.getLicensePlateText().isBlank()
                || result.getLicensePlateText().toUpperCase().contains("TESPIT")) {
            dto.setConfidence(result.getConfidence());
            return dto;
        }

        String plate = result.getLicensePlateText().trim();
        String normalized = PlateMatcher.normalize(plate);
        dto.setLicensePlate(plate);
        dto.setNormalizedPlate(normalized);
        dto.setConfidence(result.getConfidence());

        ParkingSession session = repository.getLeavingOrActiveSessionByPlate(normalized);
        if (session == null) {
            PlateMatcher.MatchResult<ParkingSession> fuzzy = PlateMatcher.findBestMatch(
                normalized,
                repository.getLeavingSessions(),
                s -> s.getVehicle() != null && s.getVehicle().getLicensePlate() != null
                    ? s.getVehicle().getLicensePlate().getPlateNumber() : "",
                PLATE_MATCH_THRESHOLD);
            if (fuzzy != null) {
                session = fuzzy.getItem();
            }
        }
        if (session == null) {
            return dto;
        }

        session.complete();
        repository.updateSession(session);
        Vehicle vehicle = session.getVehicle();
        if (vehicle != null) {
            vehicle.setExitTime(LocalDateTime.now());
            repository.saveVehicle(vehicle);
        }

        dto.setSessionId(session.getSessionId());
        dto.setSlotId(session.getParkingSlot() != null ? session.getParkingSlot().getSlotId() : null);
        dto.setExited(true);

        notificationService.sendToPlate(plate, "Çıkış onaylandı", "İyi yolculuklar!");
        LiveParkingStatusDto live = liveParkingService.getLiveStatus(
            session.getParkingSlot() != null ? resolveAreaFromSlot(session.getParkingSlot().getSlotId()) : null,
            null, null);
        if (live != null) {
            broadcaster.broadcastLiveStatus(live);
        }
        return dto;
    }

    private String resolveAreaFromSlot(String slotId) {
        if (slotId == null) return null;
        for (ParkingArea area : repository.getAllAreas()) {
            if (area.getSlotById(slotId) != null) {
                return area.getAreaId();
            }
        }
        return null;
    }

    private byte[] tryGetAnnotatedImage(byte[] imageBytes) {
        try {
            byte[] annotated = yoloInference.getParkingSlotsAnnotatedImage(imageBytes);
            if (annotated != null && annotated.length > 0) return annotated;
        } catch (Exception ignored) {
        }
        return null;
    }
}
