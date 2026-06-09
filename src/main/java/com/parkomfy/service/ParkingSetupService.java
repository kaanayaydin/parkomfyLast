package com.parkomfy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.api.*;
import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Base64;

/**
 * Admin parking area registration: photo → model predict → manual corners → save.
 */
public class ParkingSetupService {

    private final IParkingRepository repository;
    private final IYOLOInference yoloInference;
    private final ObjectMapper mapper = new ObjectMapper();

    public ParkingSetupService(IParkingRepository repository, IYOLOInference yoloInference) {
        this.repository = repository;
        this.yoloInference = yoloInference;
    }

    private static final Set<String> ALLOWED_VIDEOS = Set.of("loop1", "loop2", "loop3");

    public void resetParkingData() {
        repository.resetAllParkingData();
        clearCalibrationFiles();
    }

    public ParkingAreaDto createParkingArea(CreateParkingAreaRequest req) {
        if (req.getAreaName() == null || req.getAreaName().isBlank()) {
            throw new IllegalArgumentException("areaName is required");
        }
        String areaId = repository.nextAreaId();
        String lotKey = normalizeVideoLotKey(req.getLotKey());
        for (ParkingArea existing : repository.getAllAreas()) {
            String existingKey = repository.getLotKey(existing.getAreaId());
            if (lotKey.equals(existingKey)) {
                throw new IllegalArgumentException(
                    "Bu kamera videosu zaten kullanılıyor: " + lotKey + " (" + existing.getAreaName() + ")");
            }
        }
        ParkingArea area = new ParkingArea(areaId, req.getAreaName().trim(),
            req.getAddress() != null ? req.getAddress().trim() : "");
        repository.saveAreaFull(area, lotKey);
        ParkingAreaDto dto = new ParkingAreaDto();
        dto.setAreaId(areaId);
        dto.setAreaName(area.getAreaName());
        dto.setAddress(area.getAddress());
        dto.setLotKey(lotKey);
        dto.setCalibrated(false);
        dto.setSlotCount(0);
        return dto;
    }

    public SlotPredictionResultDto predictSlotsWithReference(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("image is required");
        }
        int imageWidth = 1280;
        int imageHeight = 720;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img != null) {
                imageWidth = img.getWidth();
                imageHeight = img.getHeight();
            }
        } catch (Exception ignored) {
            // JPEG boyutu okunamazsa varsayılan kullanılır
        }

        SlotPredictionResultDto result = new SlotPredictionResultDto();
        result.setSlots(mapDetectedSlots(imageBytes));
        result.setImageWidth(imageWidth);
        result.setImageHeight(imageHeight);
        result.setImageBase64(Base64.getEncoder().encodeToString(imageBytes));
        return result;
    }

    private List<PredictedSlotDto> mapDetectedSlots(byte[] imageBytes) {
        List<ParkingSlotResultDto> detected;
        try {
            detected = yoloInference.detectParkingSlots(imageBytes);
        } catch (Exception e) {
            throw new IllegalStateException(
                "gRPC/model hatası: " + e.getMessage()
                + " — Python server.py çalışıyor mu? PARKOMFY_SLOT_MODEL=best.pt");
        }
        if (detected == null || detected.isEmpty()) {
            throw new IllegalStateException(
                "Model slot bulamadı. Otopark fotoğrafının tam görünür olduğundan emin olun "
                + "(best.pt sınıfları: Bos, Dolu).");
        }
        List<PredictedSlotDto> out = new ArrayList<>();
        int n = 1;
        for (ParkingSlotResultDto d : detected) {
            PredictedSlotDto p = new PredictedSlotDto();
            p.setSlotNumber(n++);
            p.setOccupied(d.isOccupied());
            p.setConfidence(d.getConfidence());
            if (d.getCorners() != null && d.getCorners().size() >= 4) {
                for (double[] c : d.getCorners()) {
                    p.getCorners().add(new SlotCornerDto(c[0], c[1]));
                }
            } else {
                p.getCorners().add(new SlotCornerDto(d.getX(), d.getY()));
                p.getCorners().add(new SlotCornerDto(d.getX() + d.getWidth(), d.getY()));
                p.getCorners().add(new SlotCornerDto(d.getX() + d.getWidth(), d.getY() + d.getHeight()));
                p.getCorners().add(new SlotCornerDto(d.getX(), d.getY() + d.getHeight()));
            }
            out.add(p);
        }
        return out;
    }

    public ParkingAreaDto saveCalibration(SaveCalibrationRequest req) {
        if (req.getAreaId() == null || req.getAreaId().isBlank()) {
            throw new IllegalArgumentException("areaId is required");
        }
        if (req.getSlots() == null || req.getSlots().isEmpty()) {
            throw new IllegalArgumentException("at least one slot required");
        }
        ParkingArea area = repository.getArea(req.getAreaId());
        if (area == null) {
            throw new IllegalArgumentException("Parking area not found: " + req.getAreaId());
        }
        String lotKey = req.getLotKey() != null ? req.getLotKey() : repository.getLotKey(req.getAreaId());

        repository.deleteSlotsForArea(req.getAreaId());

        List<Map<String, Object>> jsonSlots = new ArrayList<>();
        for (SaveCalibrationRequest.CalibratedSlotDto s : req.getSlots()) {
            String slotId = "SLOT-" + lotKey + "-" + s.getSlotNumber();
            ParkingSlot slot = new ParkingSlot(slotId, 0, "A", s.getSlotNumber());
            applyCorners(slot, s.getCorners());
            slot.setStatus(s.isOccupied()
                ? ParkingSlot.SlotStatus.OCCUPIED
                : ParkingSlot.SlotStatus.AVAILABLE);
            repository.insertSlot(req.getAreaId(), slot);

            Map<String, Object> js = new LinkedHashMap<>();
            js.put("slot_number", s.getSlotNumber());
            js.put("slot_id", slotId);
            List<double[]> corners = new ArrayList<>();
            for (SlotCornerDto c : s.getCorners()) {
                corners.add(new double[]{c.getX(), c.getY()});
            }
            js.put("corners", corners);
            jsonSlots.add(js);
        }

        writeCalibrationJson(req.getAreaId(), req.getImageWidth(), req.getImageHeight(), jsonSlots);
        repository.markAreaCalibrated(req.getAreaId(), true);

        ParkingAreaDto dto = new ParkingAreaDto();
        dto.setAreaId(req.getAreaId());
        dto.setAreaName(area.getAreaName());
        dto.setAddress(area.getAddress());
        dto.setLotKey(lotKey);
        dto.setCalibrated(true);
        dto.setSlotCount(req.getSlots().size());
        return dto;
    }

    public List<ParkingAreaDto> listAreas() {
        List<ParkingAreaDto> list = new ArrayList<>();
        for (ParkingArea a : repository.getAllAreas()) {
            ParkingAreaDto dto = new ParkingAreaDto();
            dto.setAreaId(a.getAreaId());
            dto.setAreaName(a.getAreaName());
            dto.setAddress(a.getAddress());
            dto.setLotKey(repository.getLotKey(a.getAreaId()));
            dto.setCalibrated(repository.isAreaCalibrated(a.getAreaId()));
            dto.setSlotCount(a.getParkingSlots().size());
            list.add(dto);
        }
        return list;
    }

    private void applyCorners(ParkingSlot slot, List<SlotCornerDto> corners) {
        if (corners == null || corners.size() < 4) return;
        slot.setC1x(corners.get(0).getX()); slot.setC1y(corners.get(0).getY());
        slot.setC2x(corners.get(1).getX()); slot.setC2y(corners.get(1).getY());
        slot.setC3x(corners.get(2).getX()); slot.setC3y(corners.get(2).getY());
        slot.setC4x(corners.get(3).getX()); slot.setC4y(corners.get(3).getY());
        double minX = Math.min(Math.min(corners.get(0).getX(), corners.get(1).getX()),
            Math.min(corners.get(2).getX(), corners.get(3).getX()));
        double minY = Math.min(Math.min(corners.get(0).getY(), corners.get(1).getY()),
            Math.min(corners.get(2).getY(), corners.get(3).getY()));
        double maxX = Math.max(Math.max(corners.get(0).getX(), corners.get(1).getX()),
            Math.max(corners.get(2).getX(), corners.get(3).getX()));
        double maxY = Math.max(Math.max(corners.get(0).getY(), corners.get(1).getY()),
            Math.max(corners.get(2).getY(), corners.get(3).getY()));
        slot.setXCoordinate(minX);
        slot.setYCoordinate(minY);
        slot.setSlotWidth(maxX - minX);
        slot.setSlotHeight(maxY - minY);
    }

    private String normalizeVideoLotKey(String lotKey) {
        if (lotKey == null || lotKey.isBlank()) {
            throw new IllegalArgumentException("Kamera videosu seçin (loop1, loop2 veya loop3)");
        }
        String key = lotKey.trim().toLowerCase().replace(".mp4", "");
        if (!ALLOWED_VIDEOS.contains(key)) {
            throw new IllegalArgumentException("Geçersiz video: " + lotKey + " (loop1, loop2, loop3)");
        }
        return key;
    }

    private void clearCalibrationFiles() {
        try {
            Path dir = Paths.get("grpc_server", "calibrations");
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                    });
            }
        } catch (Exception e) {
            throw new IllegalStateException("Kalibrasyon dosyaları silinemedi: " + e.getMessage());
        }
    }

    private void writeCalibrationJson(String areaId, int w, int h, List<Map<String, Object>> slots) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("area_id", areaId);
            root.put("image_width", w);
            root.put("image_height", h);
            root.put("slots", slots);
            Path dir = Paths.get("grpc_server", "calibrations");
            Files.createDirectories(dir);
            Path file = dir.resolve(areaId + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
        } catch (Exception e) {
            throw new IllegalStateException("Calibration JSON yazılamadı: " + e.getMessage());
        }
    }
}
