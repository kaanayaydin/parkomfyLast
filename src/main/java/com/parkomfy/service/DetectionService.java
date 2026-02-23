package com.parkomfy.service;

import com.parkomfy.ai.IYOLOInference;
import com.parkomfy.ai.YOLOInference;
import com.parkomfy.model.*;
import com.parkomfy.ocr.ILicensePlateReader;
import com.parkomfy.ocr.LicensePlateReader;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.util.SpatialAnalysisUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Detection business logic: coordinates YOLO inference, OCR, repository. Uses DI for YOLO/OCR.
 */
public class DetectionService implements IDetectionService {

    private static final double IOU_VALID_THRESHOLD = 0.5;
    private static final int MULTI_FRAME_CONSISTENCY_COUNT = 3;
    private static final double MIN_PLATE_CONFIDENCE = 0.5;

    private final IParkingRepository repository;
    private final IYOLOInference yoloInference;
    private final ILicensePlateReader licensePlateReader;

    private double totalDetections = 0;
    private double correctDetections = 0;

    /** DI: repository, YOLO impl, OCR impl. */
    public DetectionService(IParkingRepository repository,
                           IYOLOInference yoloInference,
                           ILicensePlateReader licensePlateReader) {
        if (repository == null) {
            throw new IllegalArgumentException("Repository cannot be null");
        }
        if (yoloInference == null) {
            throw new IllegalArgumentException("YOLOInference cannot be null");
        }
        if (licensePlateReader == null) {
            throw new IllegalArgumentException("LicensePlateReader cannot be null");
        }
        this.repository = repository;
        this.yoloInference = yoloInference;
        this.licensePlateReader = licensePlateReader;
    }

    /** Backward compat: default YOLO and OCR. */
    public DetectionService(IParkingRepository repository) {
        this(repository, new YOLOInference(), new LicensePlateReader());
    }

    /** YOLO only; default license plate reader. */
    public DetectionService(IParkingRepository repository, YOLOInference yoloInference) {
        this(repository, yoloInference, new LicensePlateReader());
    }

    /** IoU between detection box and slot. If IoU < 0.5, logs INVALID_GEOMETRY and returns false. */
    public boolean isValidGeometry(BoundingBoxDto detectionBox, ParkingSlot slot) {
        double iou = SpatialAnalysisUtil.computeIoU(
                detectionBox.getX(), detectionBox.getY(), detectionBox.getWidth(), detectionBox.getHeight(),
                slot.getXCoordinate(), slot.getYCoordinate(), slot.getSlotWidth(), slot.getSlotHeight()
        );
        if (iou < IOU_VALID_THRESHOLD) {
            CVFailureLog.logFailure(
                    CVFailureLog.FailureCategory.INVALID_GEOMETRY,
                    "IoU below threshold: " + String.format("%.3f", iou) + " < " + IOU_VALID_THRESHOLD,
                    null,
                    slot.getSlotId(),
                    "detectionBox=(" + detectionBox.getX() + "," + detectionBox.getY() + "," + detectionBox.getWidth() + "," + detectionBox.getHeight() + ")"
            );
            return false;
        }
        return true;
    }

    /** Require same plate text and min confidence in 3 frames; return LICENSE_PLATE result or null. */
    public DetectionResult validatePlateWithMultiFrame(List<PlateReading> readings, String cameraId) {
        if (readings == null || readings.size() < MULTI_FRAME_CONSISTENCY_COUNT) {
            return null;
        }
        String first = null;
        double sumConf = 0;
        int count = 0;
        for (int i = 0; i < MULTI_FRAME_CONSISTENCY_COUNT; i++) {
            PlateReading r = readings.get(i);
            if (r.getPlateText() == null || r.getPlateText().isEmpty() || r.getConfidence() < MIN_PLATE_CONFIDENCE) {
                CVFailureLog.logFailure(CVFailureLog.FailureCategory.LOW_CONFIDENCE,
                        "Frame " + (i + 1) + " low confidence or empty plate",
                        cameraId, null, "confidence=" + r.getConfidence());
                return null;
            }
            if (first == null) {
                first = r.getPlateText().trim();
            } else if (!first.equals(r.getPlateText().trim())) {
                CVFailureLog.logFailure(CVFailureLog.FailureCategory.OCR_MISREAD,
                        "Plate inconsistent across frames: " + first + " vs " + r.getPlateText(),
                        cameraId, null, null);
                return null;
            }
            sumConf += r.getConfidence();
            count++;
        }
        double avgConf = count > 0 ? sumConf / count : 0;
        DetectionResult result = new DetectionResult(cameraId, null, true, avgConf);
        result.setLicensePlateText(first);
        result.setDetectionType(DetectionResult.DetectionType.LICENSE_PLATE);
        return result;
    }

    @Override
    public DetectionResult detectOccupancy(Camera camera, ParkingSlot slot) {
        if (!camera.isActive()) {
            throw new IllegalStateException("Camera " + camera.getCameraId() + " is not active");
        }

        boolean vehicleDetected = yoloInference.detectVehicle(camera, slot);
        double confidence = yoloInference.getConfidence();

        List<BoundingBoxDto> boxes = yoloInference.getLastBoundingBoxes();
        if (confidence < MIN_PLATE_CONFIDENCE) {
            CVFailureLog.logFailure(CVFailureLog.FailureCategory.LOW_CONFIDENCE,
                    "Occupancy detection low confidence: " + String.format("%.3f", confidence),
                    camera.getCameraId(), slot.getSlotId(), null);
        }
        if (!boxes.isEmpty()) {
            BoundingBoxDto best = boxes.get(0);
            for (BoundingBoxDto b : boxes) {
                double iou = SpatialAnalysisUtil.computeIoU(
                        b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                        slot.getXCoordinate(), slot.getYCoordinate(), slot.getSlotWidth(), slot.getSlotHeight()
                );
                double bestIou = SpatialAnalysisUtil.computeIoU(
                        best.getX(), best.getY(), best.getWidth(), best.getHeight(),
                        slot.getXCoordinate(), slot.getYCoordinate(), slot.getSlotWidth(), slot.getSlotHeight()
                );
                if (iou > bestIou) {
                    best = b;
                }
            }
            if (!isValidGeometry(best, slot)) {
                vehicleDetected = false;
            }
        }

        DetectionResult result = new DetectionResult(
                camera.getCameraId(),
                slot.getSlotId(),
                vehicleDetected,
                confidence
        );
        result.setDetectionType(DetectionResult.DetectionType.OCCUPANCY);

        totalDetections++;
        if (vehicleDetected == slot.isOccupied()) {
            correctDetections++;
        }

        repository.saveDetectionResult(result);
        return result;
    }

    /** LPR from 3 frames; set camera currentFrame before each call. If frames null or <3, fallback to single frame. */
    public DetectionResult detectLicensePlateFromMultiFrames(Camera camera, List<byte[]> frames) {
        if (camera.getType() != Camera.CameraType.ENTRANCE_LPR) {
            throw new IllegalArgumentException("Camera must be ENTRANCE_LPR type");
        }
        if (frames != null && frames.size() >= MULTI_FRAME_CONSISTENCY_COUNT) {
            List<PlateReading> readings = new ArrayList<>();
            for (int i = 0; i < MULTI_FRAME_CONSISTENCY_COUNT; i++) {
                camera.setCurrentFrame(frames.get(i));
                String text = yoloInference.detectLicensePlateBoundingBox(camera);
                double conf = yoloInference.getConfidence();
                readings.add(new PlateReading(text != null ? text : "", conf));
            }
            DetectionResult result = validatePlateWithMultiFrame(readings, camera.getCameraId());
            if (result != null) {
                repository.saveDetectionResult(result);
                return result;
            }
            return null;
        }
        return detectLicensePlate(camera);
    }

    @Override
    public DetectionResult detectLicensePlate(Camera camera) {
        if (camera.getType() != Camera.CameraType.ENTRANCE_LPR) {
            throw new IllegalArgumentException("Camera must be ENTRANCE_LPR type");
        }

        String licensePlateText = yoloInference.detectLicensePlateBoundingBox(camera);
        double detectionConfidence = yoloInference.getConfidence();

        if (licensePlateText != null && !licensePlateText.isEmpty()) {
            boolean isValid = licensePlateReader.validateLicensePlateFormat(licensePlateText, "TR");
            if (!isValid) {
                CVFailureLog.logFailure(CVFailureLog.FailureCategory.OCR_MISREAD,
                        "Invalid plate format: " + licensePlateText, camera.getCameraId(), null, "TR");
            }
        }

        DetectionResult result = new DetectionResult(
                camera.getCameraId(),
                null,
                licensePlateText != null && !licensePlateText.isEmpty(),
                detectionConfidence
        );
        result.setLicensePlateText(licensePlateText);
        result.setDetectionType(DetectionResult.DetectionType.LICENSE_PLATE);
        repository.saveDetectionResult(result);
        return result;
    }

    @Override
    public void processDetectionResult(DetectionResult result) {
        if (result.getDetectionType() == DetectionResult.DetectionType.OCCUPANCY) {
            ParkingSlot slot = repository.getSlot(result.getSlotId());
            if (slot != null) {
                if (result.isVehicleDetected() && result.isHighConfidence()) {
                    if (!slot.isOccupied()) {
                        // Could trigger LPR and session creation
                    }
                } else if (!result.isVehicleDetected() && result.isHighConfidence()) {
                    if (slot.isOccupied()) {
                        slot.vacate();
                        repository.updateSlot(slot);
                    }
                }
            }
        }
    }

    @Override
    public List<DetectionResult> batchDetect(Camera camera, List<ParkingSlot> slots) {
        List<DetectionResult> results = new ArrayList<>();

        for (ParkingSlot slot : slots) {
            DetectionResult result = detectOccupancy(camera, slot);
            results.add(result);
        }

        return results;
    }

    @Override
    public double getDetectionAccuracy() {
        if (totalDetections == 0) return 0.0;
        return (correctDetections / totalDetections) * 100.0;
    }

    /** Total detection count. */
    public long getTotalDetections() {
        return (long) totalDetections;
    }

    /** Correct detection count. */
    public long getCorrectDetections() {
        return (long) correctDetections;
    }
}
