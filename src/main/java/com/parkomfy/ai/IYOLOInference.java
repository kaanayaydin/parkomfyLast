package com.parkomfy.ai;

import com.parkomfy.model.BoundingBoxDto;
import com.parkomfy.model.Camera;
import com.parkomfy.model.ParkingSlot;
import com.parkomfy.model.ParkingSlotResultDto;

import java.util.List;

/**
 * YOLO inference interface. Swap implementations (ONNX, Python gRPC, DJL) without changing business logic.
 */
public interface IYOLOInference {

    /** Detect vehicle in slot via gRPC. */
    boolean detectVehicle(Camera camera, ParkingSlot slot);

    /** Detect license plate in frame; returns text or null. */
    String detectLicensePlateBoundingBox(Camera camera);

    /** Last vehicle detection bounding boxes (for IoU). */
    List<BoundingBoxDto> getLastBoundingBoxes();

    /** Last detection confidence. */
    double getConfidence();

    /** Run detection on raw frame bytes; returns true if vehicle detected. */
    boolean processAndDetect(byte[] frameData);

    /** Park slotlarını tespit et (poligon ROI, IoU); boş/dolu ve güven skoru ile döner. */
    List<ParkingSlotResultDto> detectParkingSlots(byte[] imageData);

    /** OpenCV ile çizilmiş slot görseli (yeşil/kırmızı poligon, OCCUPIED). Hata durumunda null. */
    byte[] getParkingSlotsAnnotatedImage(byte[] imageData);
}
