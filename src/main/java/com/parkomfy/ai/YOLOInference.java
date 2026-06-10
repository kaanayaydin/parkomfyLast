package com.parkomfy.ai;

import com.google.protobuf.ByteString;
import com.parkomfy.model.BoundingBoxDto;
import com.parkomfy.model.Camera;
import com.parkomfy.model.ParkingSlot;
import com.parkomfy.model.ParkingSlotResultDto;
import com.parkomfy.grpc.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** YOLO inference via gRPC blocking stub to Python YOLO service. No simulation; all results from gRPC. */
public class YOLOInference implements IYOLOInference {

    private static final double DEFAULT_CONFIDENCE = 0.0;
    /** gRPC çağrı zaman aşımı (saniye); takılı kalmayı önler */
    private static final int GRPC_DEADLINE_SECONDS = 15;
    /** Slot modeli ilk yüklemede uzun sürebilir (best.pt ~500MB) */
    private static final int GRPC_SLOT_DEADLINE_SECONDS = 90;

    private ManagedChannel channel;
    private YOLODetectionServiceGrpc.YOLODetectionServiceBlockingStub blockingStub;
    private final String grpcHost;
    private final int grpcPort;

    private double lastConfidence = DEFAULT_CONFIDENCE;
    private DetectionResponse lastDetectionResponse;
    private LicensePlateResponse lastLicensePlateResponse;

    /** Default: gRPC at localhost:50051. */
    public YOLOInference() {
        this("localhost", 50051);
    }

    public YOLOInference(String grpcHost, int grpcPort) {
        this.grpcHost = grpcHost;
        this.grpcPort = grpcPort;
        initializeChannel();
    }

    private void initializeChannel() {
        try {
            channel = ManagedChannelBuilder
                    .forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .build();
            blockingStub = YOLODetectionServiceGrpc.newBlockingStub(channel);
        } catch (Exception e) {
            throw new RuntimeException("gRPC channel init failed: " + e.getMessage(), e);
        }
    }

    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    public boolean detectVehicle(Camera camera, ParkingSlot slot) {
        if (!camera.isActive()) {
            throw new IllegalStateException("Camera " + camera.getCameraId() + " is not active");
        }

        byte[] frameData = camera.getCurrentFrame() != null ? camera.getCurrentFrame() : new byte[0];
        DetectionRequest request = DetectionRequest.newBuilder()
                .setCameraId(camera.getCameraId())
                .setImageData(ByteString.copyFrom(frameData))
                .setSlotId(slot != null ? slot.getSlotId() : "")
                .build();

        DetectionResponse response = blockingStub
                .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .detectVehicles(request);
        lastDetectionResponse = response;
        lastConfidence = response.getConfidence();
        return response.getVehicleDetected();
    }

    @Override
    public String detectLicensePlateBoundingBox(Camera camera) {
        if (camera.getType() != Camera.CameraType.ENTRANCE_LPR) {
            throw new IllegalArgumentException("Camera must be ENTRANCE_LPR type for license plate detection");
        }

        byte[] frameData = camera.getCurrentFrame() != null ? camera.getCurrentFrame() : new byte[0];
        LicensePlateRequest request = LicensePlateRequest.newBuilder()
                .setCameraId(camera.getCameraId())
                .setImageData(ByteString.copyFrom(frameData))
                .build();

        LicensePlateResponse response = blockingStub
                .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .detectLicensePlate(request);
        lastLicensePlateResponse = response;
        lastConfidence = response.getConfidence();
        String text = response.getLicensePlateText();
        return (text != null && !text.isEmpty()) ? text : null;
    }

    @Override
    public List<BoundingBoxDto> getLastBoundingBoxes() {
        if (lastDetectionResponse == null) {
            return Collections.emptyList();
        }
        List<BoundingBoxDto> list = new ArrayList<>();
        for (BoundingBox bb : lastDetectionResponse.getBoundingBoxesList()) {
            list.add(new BoundingBoxDto(
                    bb.getX(), bb.getY(), bb.getWidth(), bb.getHeight(),
                    bb.getConfidence()
            ));
        }
        return list;
    }

    @Override
    public double getConfidence() {
        return lastConfidence;
    }

    @Override
    public boolean processAndDetect(byte[] frameData) {
        if (frameData == null) {
            frameData = new byte[0];
        }
        DetectionRequest request = DetectionRequest.newBuilder()
                .setImageData(ByteString.copyFrom(frameData))
                .build();
        DetectionResponse response = blockingStub
                .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .detectVehicles(request);
        lastDetectionResponse = response;
        lastConfidence = response.getConfidence();
        return response.getVehicleDetected();
    }

    @Override
    public List<ParkingSlotResultDto> detectParkingSlots(byte[] imageData) {
        return detectParkingSlots(imageData, "");
    }

    @Override
    public List<ParkingSlotResultDto> detectParkingSlots(byte[] imageData, String areaId) {
        if (imageData == null) {
            imageData = new byte[0];
        }
        DetectionRequest.Builder b = DetectionRequest.newBuilder()
                .setImageData(ByteString.copyFrom(imageData));
        if (areaId != null && !areaId.isBlank()) {
            b.setCameraId(areaId);
        }
        DetectionRequest request = b.build();
        ParkingSlotsResponse response = blockingStub
                .withDeadlineAfter(GRPC_SLOT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .detectParkingSlots(request);
        List<ParkingSlotResultDto> list = new ArrayList<>();
        for (ParkingSlotResult r : response.getSlotsList()) {
            List<double[]> corners = new ArrayList<>();
            for (com.parkomfy.grpc.Point2D p : r.getCornersList()) {
                corners.add(new double[]{ p.getX(), p.getY() });
            }
            list.add(new ParkingSlotResultDto(
                    r.getX(), r.getY(), r.getWidth(), r.getHeight(),
                    r.getOccupied(), r.getConfidence(), corners,
                    r.getSlotNumber(), r.getVehicleX(), r.getVehicleY(),
                    r.getVehicleWidth(), r.getVehicleHeight()));
        }
        return list;
    }

    @Override
    public String detectLicensePlateFromCrop(byte[] vehicleCropJpeg, int slotNumber) {
        if (vehicleCropJpeg == null || vehicleCropJpeg.length < 50) {
            return null;
        }
        LicensePlateRequest request = LicensePlateRequest.newBuilder()
                .setCameraId("slot-" + slotNumber)
                .setImageData(ByteString.copyFrom(vehicleCropJpeg))
                .build();
        try {
            LicensePlateResponse response = blockingStub
                    .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .detectLicensePlate(request);
            String text = response.getLicensePlateText();
            if (text == null || text.isBlank()) {
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public PlateRead readPlateFromCrop(byte[] vehicleCropJpeg, int slotNumber) {
        if (vehicleCropJpeg == null || vehicleCropJpeg.length < 50) {
            return new PlateRead(null, 0.0);
        }
        LicensePlateRequest request = LicensePlateRequest.newBuilder()
                .setCameraId("slot-" + slotNumber)
                .setImageData(ByteString.copyFrom(vehicleCropJpeg))
                .build();
        try {
            LicensePlateResponse response = blockingStub
                    .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .detectLicensePlate(request);
            String text = response.getLicensePlateText();
            if (text == null || text.isBlank()) {
                return new PlateRead(null, 0.0);
            }
            return new PlateRead(text.trim(), response.getConfidence());
        } catch (Exception e) {
            return new PlateRead(null, 0.0);
        }
    }

    @Override
    public byte[] getParkingSlotsAnnotatedImage(byte[] imageData) {
        return getParkingSlotsAnnotatedImage(imageData, "");
    }

    @Override
    public byte[] getParkingSlotsAnnotatedImage(byte[] imageData, String areaId) {
        if (imageData == null) {
            imageData = new byte[0];
        }
        DetectionRequest.Builder b = DetectionRequest.newBuilder()
                .setImageData(ByteString.copyFrom(imageData));
        if (areaId != null && !areaId.isBlank()) {
            b.setCameraId(areaId);
        }
        DetectionRequest request = b.build();
        ParkSlotsImageResponse response = blockingStub
                .withDeadlineAfter(GRPC_SLOT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .detectParkingSlotsImage(request);
        byte[] bytes = response.getImageJpeg().toByteArray();
        return bytes.length > 0 ? bytes : null;
    }
}
