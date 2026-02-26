#!/usr/bin/env python3
"""
PARKOMFY YOLO gRPC Sunucusu (detection.proto uyumlu)
Port: 50051
Gerçek YOLOv8 ile araç tespiti (car, bus, truck). ultralytics yoksa simülasyon.
"""
import io
import logging
from concurrent.futures import ThreadPoolExecutor

import grpc
import detection_pb2
import detection_pb2_grpc

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

PORT = 50051

# COCO: 2=car, 5=bus, 7=truck
VEHICLE_CLASS_IDS = {2, 5, 7}
MIN_CONFIDENCE = 0.25

_yolo_model = None

def _load_yolo():
    global _yolo_model
    if _yolo_model is not None:
        return _yolo_model
    try:
        from ultralytics import YOLO
        _yolo_model = YOLO("yolov8n.pt")
        logger.info("YOLOv8 modeli yüklendi (yolov8n.pt)")
        return _yolo_model
    except Exception as e:
        logger.warning("YOLOv8 yüklenemedi, simülasyon kullanılacak: %s", e)
        return None


def _detect_vehicles_yolo(image_bytes):
    """Görüntü üzerinde araç tespiti; normalize (0-1) x,y,w,h ve confidence döner."""
    model = _load_yolo()
    if model is None:
        return [], False, 0.0
    try:
        from PIL import Image
        import numpy as np
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        img_np = np.array(img)
        h, w = img_np.shape[0], img_np.shape[1]
        results = model(img_np, verbose=False)[0]
        boxes_out = []
        max_conf = 0.0
        for box in results.boxes:
            cls_id = int(box.cls[0])
            if cls_id not in VEHICLE_CLASS_IDS:
                continue
            conf = float(box.conf[0])
            if conf < MIN_CONFIDENCE:
                continue
            xyxy = box.xyxy[0].cpu().numpy()
            x1, y1, x2, y2 = xyxy[0], xyxy[1], xyxy[2], xyxy[3]
            # Normalize: sol üst x,y ve width, height (0-1)
            xn = x1 / w
            yn = y1 / h
            wn = (x2 - x1) / w
            hn = (y2 - y1) / h
            name = results.names.get(cls_id, "vehicle")
            boxes_out.append(detection_pb2.BoundingBox(
                x=float(xn), y=float(yn), width=float(wn), height=float(hn),
                class_name=name, confidence=conf
            ))
            max_conf = max(max_conf, conf)
        vehicle_detected = len(boxes_out) > 0
        avg_conf = (sum(b.confidence for b in boxes_out) / len(boxes_out)) if boxes_out else 0.0
        logger.info("YOLOv8: %s araç tespit edildi, ortalama güven=%.2f", len(boxes_out), avg_conf)
        return boxes_out, vehicle_detected, avg_conf
    except Exception as e:
        logger.exception("YOLOv8 inference hatası: %s", e)
        return [], False, 0.0


def _detect_vehicles_simulation(image_data):
    """Simülasyon: 4 küçük kutu (gerçek tespit yok)."""
    if len(image_data) < 500:
        return [], False, 0.25
    # Ön sırada 4 araç varmış gibi küçük kutular (normalize 0-1)
    boxes = [
        detection_pb2.BoundingBox(x=0.05, y=0.55, width=0.2, height=0.25, class_name="vehicle", confidence=0.88),
        detection_pb2.BoundingBox(x=0.28, y=0.55, width=0.2, height=0.25, class_name="vehicle", confidence=0.85),
        detection_pb2.BoundingBox(x=0.51, y=0.55, width=0.2, height=0.25, class_name="vehicle", confidence=0.82),
        detection_pb2.BoundingBox(x=0.74, y=0.55, width=0.2, height=0.25, class_name="vehicle", confidence=0.80),
    ]
    return boxes, True, 0.84


class YOLODetectionServicer(detection_pb2_grpc.YOLODetectionServiceServicer):
    """detection.proto YOLODetectionService implementasyonu."""

    def DetectVehicles(self, request, context):
        logger.info("DetectVehicles isteği alındı")
        image_data = request.image_data if request.image_data else b""
        bounding_boxes, vehicle_detected, confidence = _detect_vehicles_yolo(image_data)
        if _yolo_model is None:
            bounding_boxes, vehicle_detected, confidence = _detect_vehicles_simulation(image_data)
        return detection_pb2.DetectionResponse(
            vehicle_detected=vehicle_detected,
            confidence=confidence,
            bounding_boxes=bounding_boxes,
        )

    def DetectLicensePlate(self, request, context):
        image_data = request.image_data if request.image_data else b""
        # Simülasyon: görüntü varsa örnek plaka döndür
        if len(image_data) > 500:
            license_plate_text = "34 ABC 123"
            confidence = 0.85
        else:
            license_plate_text = ""
            confidence = 0.0
        logger.info("DetectLicensePlate: camera_id=%s, image_len=%s -> plate=%s",
                    request.camera_id, len(image_data), license_plate_text)
        return detection_pb2.LicensePlateResponse(
            license_plate_text=license_plate_text,
            confidence=confidence,
            # plate_location isteğe bağlı
        )

    def DetectBatch(self, request_iterator, context):
        for req in request_iterator:
            image_data = req.image_data if req.image_data else b""
            vehicle_detected = len(image_data) > 500
            confidence = 0.88 if vehicle_detected else 0.25
            yield detection_pb2.DetectionResponse(
                vehicle_detected=vehicle_detected,
                confidence=confidence,
                bounding_boxes=[],
            )


def serve():
    # Thread pool olmadan istekler takılabiliyor; 4 thread ile cevap ver
    executor = ThreadPoolExecutor(max_workers=4)
    server = grpc.server(executor)
    detection_pb2_grpc.add_YOLODetectionServiceServicer_to_server(
        YOLODetectionServicer(), server
    )
    # 127.0.0.1 = sadece bu makine; Java localhost ile bağlansın diye
    listen_addr = f"127.0.0.1:{PORT}"
    server.add_insecure_port(listen_addr)
    server.start()
    logger.info("YOLO gRPC sunucusu dinleniyor: %s (Java bu adrese bağlansın)", listen_addr)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
