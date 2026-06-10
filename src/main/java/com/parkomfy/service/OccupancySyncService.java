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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hibrit doluluk: kalibre poligon + best.pt (Bos/Dolu); yoksa yolov8n fallback.
 * Her otopark kendi video stream'inden (loop1/2/3) eşzamanlı okunur.
 * Plaka: slot yeni dolunca araç kırpımı OCR.
 */
public class OccupancySyncService {

    private static final double MIN_OCC_CONF = 0.25;
    private static final double MIN_PLATE_CONF = 0.55;
    // Coklu-kare oylama: ayni plakayi bu kadar kez okumadan slota yazma
    // (tek-kare yanlis okumalari eler). Slot polygon maskeleme + bu = sağlam.
    private static final int MIN_PLATE_VOTES = 3;
    // slotId -> (normalize plaka -> [oy sayisi, guven toplami])
    private final Map<String, Map<String, double[]>> slotPlateVotes = new ConcurrentHashMap<>();
    private final IParkingRepository repository;
    private final IYOLOInference yoloInference;
    private final CameraSimulationService cameraSimulationService;
    private final LiveParkingService liveParkingService;
    private final ParkingEventBroadcaster broadcaster;
    private final PlateSimulationService plateSimulationService;
    private final PlateTrackingService plateTrackingService;
    private final Map<String, Boolean> girisPlayingLast = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cikisPlayingLast = new ConcurrentHashMap<>();
    private final ExecutorService gateExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gate-plate-sync");
        t.setDaemon(true);
        return t;
    });

    public OccupancySyncService(IParkingRepository repository,
                                IYOLOInference yoloInference,
                                CameraSimulationService cameraSimulationService,
                                LiveParkingService liveParkingService,
                                ParkingEventBroadcaster broadcaster,
                                PlateSimulationService plateSimulationService,
                                PlateTrackingService plateTrackingService) {
        this.repository = repository;
        this.yoloInference = yoloInference;
        this.cameraSimulationService = cameraSimulationService;
        this.liveParkingService = liveParkingService;
        this.broadcaster = broadcaster;
        this.plateSimulationService = plateSimulationService;
        this.plateTrackingService = plateTrackingService;
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

    /** Anlık hibrit/DB fiziksel doluluk — rezervasyon görünümü için (slotNumber → dolu). */
    public Map<Integer, Boolean> getPhysicalOccupancyBySlotNumber(String areaId) {
        Map<Integer, Boolean> result = new HashMap<>();
        ParkingArea area = repository.getArea(areaId);
        if (area == null) {
            return result;
        }
        if (!repository.isAreaCalibrated(areaId)) {
            for (ParkingSlot slot : area.getParkingSlots()) {
                boolean occ = slot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED
                    || repository.getActiveSessionForSlot(slot.getSlotId()) != null;
                result.put(slot.getSlotNumber(), occ);
            }
            return result;
        }
        String lotKey = repository.getLotKey(areaId);
        if (lotKey == null || lotKey.isBlank()) {
            lotKey = "loop1";
        }
        byte[] frame = cameraSimulationService.getLiveSnapshotForLot(lotKey);
        Map<Integer, ParkingSlotResultDto> bySlotNumber = new HashMap<>();
        if (frame != null && frame.length > 0) {
            try {
                for (ParkingSlotResultDto det : yoloInference.detectParkingSlots(frame, areaId)) {
                    if (det.getSlotNumber() > 0) {
                        bySlotNumber.put(det.getSlotNumber(), det);
                    }
                }
            } catch (Exception ignored) {
                // fallback DB
            }
        }
        for (ParkingSlot slot : area.getParkingSlots()) {
            ParkingSlotResultDto det = bySlotNumber.get(slot.getSlotNumber());
            boolean physical = det != null && det.isOccupied() && det.getConfidence() >= MIN_OCC_CONF;
            if (!physical) {
                physical = slot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED
                    || repository.getActiveSessionForSlot(slot.getSlotId()) != null;
            }
            result.put(slot.getSlotNumber(), physical);
        }
        return result;
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
                    session.setStatus(ParkingSession.SessionStatus.LEAVING);
                    session.setExitTime(java.time.LocalDateTime.now());
                    repository.updateSession(session);
                }
                if (dbSlot.getStatus() == ParkingSlot.SlotStatus.OCCUPIED || session != null) {
                    dbSlot.vacate();
                    repository.updateSlot(dbSlot);
                }
                // Slot bosaldi: birikmis plaka oylarini sifirla.
                slotPlateVotes.remove(dbSlot.getSlotId());
            }
        }

        int occupiedCount = 0;
        for (ParkingSlot dbSlot : dbSlots) {
            ParkingSlotResultDto det = bySlotNumber.get(dbSlot.getSlotNumber());
            if (det != null && det.isOccupied() && det.getConfidence() >= MIN_OCC_CONF) {
                occupiedCount++;
            }
        }
        watchGateEntranceComplete(areaId, lotKey);
        watchGateExitComplete(areaId, lotKey);

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

    /** Giriş plaka videosu bitince DB'ye plaka yaz (video Python'da tetiklenir). */
    private void watchGateEntranceComplete(String areaId, String lotKey) {
        if (!"loop1".equals(lotKey)) {
            return;
        }
        boolean playing = cameraSimulationService.isGatePlaying("giris");
        Boolean was = girisPlayingLast.get(areaId);
        girisPlayingLast.put(areaId, playing);
        if (Boolean.TRUE.equals(was) && !playing) {
            gateExecutor.submit(this::processEntrancePlate);
        }
    }


    /** Çıkış plaka videosu bitince çıkış OCR (video Python'da loop1 sonunda tetiklenir). */
    private void watchGateExitComplete(String areaId, String lotKey) {
        if (!"loop1".equals(lotKey)) {
            return;
        }
        boolean playing = cameraSimulationService.isGatePlaying("cikis");
        Boolean was = cikisPlayingLast.get(areaId);
        cikisPlayingLast.put(areaId, playing);
        if (Boolean.TRUE.equals(was) && !playing) {
            gateExecutor.submit(this::processExitPlate);
        }
    }

    private void processEntrancePlate() {
        byte[] snap = cameraSimulationService.getLiveSnapshotForLot("giris");
        if (snap != null && snap.length > 0) {
            plateTrackingService.processEntrance(snap);
        }
    }

    private void processExitPlate() {
        byte[] snap = cameraSimulationService.getLiveSnapshotForLot("cikis");
        if (snap != null && snap.length > 0) {
            plateTrackingService.processExit(snap);
        }
    }

    private void assignPlateFromHybridDetection(ParkingSlot slot, String areaId,
                                                byte[] frame, ParkingSlotResultDto det) {
        // Slot poligonuna maskeli OCR oku (komsu aracin plakasi kirpima girmez).
        IYOLOInference.PlateRead read = readPlateForSlot(slot, frame, det);

        // Gerçek OCR güvenli okuyamadıysa: sahte plaka ATAMA, oy da ekleme.
        if (read == null || !isValidPlate(read.text) || read.confidence < MIN_PLATE_CONF) {
            return;
        }

        String norm = PlateMatcher.normalize(read.text);
        if (norm.length() < 5) {
            return;
        }

        // Coklu-kare oylama: tek-kare yanlis okumalari ele. Ayni plaka
        // MIN_PLATE_VOTES kez okununca commit; lider olmadan slota yazma.
        Map<String, double[]> votes =
            slotPlateVotes.computeIfAbsent(slot.getSlotId(), k -> new HashMap<>());
        double[] tally = votes.computeIfAbsent(norm, k -> new double[2]);
        tally[0] += 1;
        tally[1] += read.confidence;

        String leader = null;
        double leaderCount = 0;
        for (Map.Entry<String, double[]> e : votes.entrySet()) {
            if (e.getValue()[0] > leaderCount) {
                leaderCount = e.getValue()[0];
                leader = e.getKey();
            }
        }
        if (leader == null || leaderCount < MIN_PLATE_VOTES) {
            return;
        }

        // Ayni-plaka tekilligi: bu plaka baska dolu slota atanmissa atlama
        // (slot 2'nin slot 3'un plakasini calmasini engeller).
        if (isPlateActiveElsewhere(areaId, slot.getSlotId(), leader)) {
            return;
        }

        String formatted = formatPlateForDisplay(leader);
        Vehicle vehicle = plateSimulationService.getOrCreateVehicle(formatted);
        vehicle.setCurrentAreaId(areaId);
        if (vehicle.getEntryTime() == null) {
            vehicle.setEntryTime(LocalDateTime.now());
        }
        if (slot.isAvailable()) {
            slot.occupy(vehicle);
        } else {
            slot.setCurrentVehicle(vehicle);
        }
        ParkingSession session = new ParkingSession(vehicle, slot);
        session.setEntryTime(vehicle.getEntryTime());
        repository.saveVehicle(vehicle);
        repository.saveSession(session);
        repository.updateSlot(slot);
        slotPlateVotes.remove(slot.getSlotId());
    }

    /**
     * Slotun kalibre dörtgenine maskeli kırpımdan plaka okur; kalibrasyon yoksa
     * araç bbox'ına düşer. Maskeleme komşu araçların plakasını dışlar.
     */
    private IYOLOInference.PlateRead readPlateForSlot(ParkingSlot slot, byte[] frame,
                                                      ParkingSlotResultDto det) {
        byte[] crop = null;
        if (slot.hasCalibratedCorners()) {
            double[] xs = {slot.getC1x(), slot.getC2x(), slot.getC3x(), slot.getC4x()};
            double[] ys = {slot.getC1y(), slot.getC2y(), slot.getC3y(), slot.getC4y()};
            crop = FrameCropUtil.cropPolygonMaskedJpeg(frame, xs, ys, 1.3, 0.05);
        }
        if (crop == null && det != null && det.hasVehicleBBox()) {
            crop = FrameCropUtil.cropNormalizedJpeg(
                frame, det.getVehicleX(), det.getVehicleY(),
                det.getVehicleWidth(), det.getVehicleHeight());
        }
        if (crop == null) {
            return null;
        }
        return yoloInference.readPlateFromCrop(crop, slot.getSlotNumber());
    }

    /** Verilen normalize plaka, alandaki baska bir dolu slotta aktif mi? */
    private boolean isPlateActiveElsewhere(String areaId, String slotId, String normPlate) {
        ParkingArea area = repository.getArea(areaId);
        if (area == null) {
            return false;
        }
        for (ParkingSlot s : area.getParkingSlots()) {
            if (s.getSlotId().equals(slotId)) {
                continue;
            }
            ParkingSession sess = repository.getActiveSessionForSlot(s.getSlotId());
            if (sess != null && sess.getVehicle() != null
                    && sess.getVehicle().getLicensePlate() != null) {
                String other = PlateMatcher.normalize(
                    sess.getVehicle().getLicensePlate().getPlateNumber());
                if (other.equals(normPlate)) {
                    return true;
                }
            }
        }
        return false;
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
