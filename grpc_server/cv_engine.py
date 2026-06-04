"""
CV engine: YOLOv8 vehicle detection + pickle/JSON slot polygons + pointPolygonTest occupancy.
"""
import io
import json
import logging
import os
import pickle
from pathlib import Path

import cv2
import numpy as np

logger = logging.getLogger(__name__)

VEHICLE_CLASSES = [2, 7]  # car, truck
YOLO_CONF = 0.4

_BASE_DIR = Path(__file__).resolve().parent
_yolo_model = None


def _load_yolo():
    global _yolo_model
    if _yolo_model is not None:
        return _yolo_model
    try:
        from ultralytics import YOLO
        model_path = os.environ.get("YOLO_MODEL", "yolov8n.pt")
        _yolo_model = YOLO(model_path)
        logger.info("YOLO loaded: %s", model_path)
    except Exception as e:
        logger.error("YOLO load failed: %s", e)
        _yolo_model = None
    return _yolo_model


def _decode_image(image_bytes):
    if not image_bytes:
        return None, 0, 0
    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if bgr is None:
        return None, 0, 0
    h, w = bgr.shape[:2]
    return bgr, w, h


def _slots_path_for_camera(camera_id):
    """Resolve slots_*.pkl or slots_*.json from camera_id (e.g. istasyon1 -> slots_1)."""
    cid = (camera_id or "").strip().lower()
    candidates = []
    if cid:
        if cid.startswith("istasyon"):
            n = cid.replace("istasyon", "")
            candidates.append(f"slots_{n}")
        if cid.startswith("cam-"):
            candidates.append(cid)
        candidates.append(cid)
    candidates.extend(["slots_01", "slots_1", "slots_02", "slots_2"])

    for name in candidates:
        for ext in (".pkl", ".json"):
            p = _BASE_DIR / f"{name}{ext}"
            if p.is_file():
                return p
    for p in sorted(_BASE_DIR.glob("slots_*.pkl")):
        return p
    for p in sorted(_BASE_DIR.glob("slots_*.json")):
        return p
    return None


def load_slot_polygons(camera_id):
    """List of polygons; each polygon is list of (x,y) pixel points."""
    path = _slots_path_for_camera(camera_id)
    if path is None:
        logger.warning("No slot definition file for camera_id=%s", camera_id)
        return []
    if path.suffix == ".pkl":
        with open(path, "rb") as f:
            return pickle.load(f)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return data


def _slot_index_from_id(slot_id, num_slots):
    """Map slot_id string to polygon index (0-based)."""
    if not slot_id or num_slots <= 0:
        return None
    digits = "".join(c for c in slot_id if c.isdigit())
    if digits:
        idx = int(digits) - 1
        if 0 <= idx < num_slots:
            return idx
    return None


def detect_vehicles_yolo(bgr):
    """Returns list of dicts: x1,y1,x2,y2, confidence, class_name (pixels)."""
    model = _load_yolo()
    if model is None or bgr is None:
        return []
    results = model.predict(bgr, classes=VEHICLE_CLASSES, conf=YOLO_CONF, verbose=False)
    if not results:
        return []
    boxes = results[0].boxes
    if boxes is None or len(boxes) == 0:
        return []
    out = []
    xyxy = boxes.xyxy.cpu().numpy()
    confs = boxes.conf.cpu().numpy()
    clss = boxes.cls.cpu().numpy()
    names = results[0].names
    for i in range(len(xyxy)):
        x1, y1, x2, y2 = map(int, xyxy[i][:4])
        cid = int(clss[i])
        out.append({
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "confidence": float(confs[i]),
            "class_name": names.get(cid, "vehicle"),
        })
    return out


def slot_occupied_by_point_polygon(poly, detections):
    """True if any vehicle bottom-center falls inside polygon."""
    poly_np = np.array(poly, np.int32).reshape((-1, 1, 2))
    for det in detections:
        cx = (det["x1"] + det["x2"]) // 2
        cy = det["y2"]
        if cv2.pointPolygonTest(poly_np, (cx, cy), False) >= 0:
            return True, det
    return False, None


def analyze_frame(image_bytes, camera_id="", slot_id=""):
    """
    Full occupancy analysis.
    Returns: vehicle_detected, avg_confidence, boxes_pb_list, occupied_slots, vehicle_crop_bytes
    """
    import detection_pb2

    bgr, w, h = _decode_image(image_bytes)
    if bgr is None:
        return False, 0.0, [], {}, None

    detections = detect_vehicles_yolo(bgr)
    polygons = load_slot_polygons(camera_id)

    boxes_pb = []
    for det in detections:
        boxes_pb.append(detection_pb2.BoundingBox(
            x=float(det["x1"]),
            y=float(det["y1"]),
            width=float(det["x2"] - det["x1"]),
            height=float(det["y2"] - det["y1"]),
            class_name=det["class_name"],
            confidence=det["confidence"],
        ))

    occupied_slots = {}
    vehicle_crop = None
    target_idx = _slot_index_from_id(slot_id, len(polygons)) if slot_id else None

    for i, poly in enumerate(polygons):
        occ, det = slot_occupied_by_point_polygon(poly, detections)
        occupied_slots[i] = (occ, det)
        if occ and det is not None and vehicle_crop is None:
            x1, y1, x2, y2 = det["x1"], det["y1"], det["x2"], det["y2"]
            vehicle_crop = cv2.imencode(".jpg", bgr[y1:y2, x1:x2])[1].tobytes()

    if target_idx is not None and target_idx < len(polygons):
        occ, det = occupied_slots.get(target_idx, (False, None))
        vehicle_detected = occ
        conf = det["confidence"] if det else 0.0
    else:
        vehicle_detected = len(detections) > 0
        conf = (
            sum(d["confidence"] for d in detections) / len(detections)
            if detections else 0.0
        )

    return vehicle_detected, conf, boxes_pb, occupied_slots, vehicle_crop


def crop_vehicle_from_frame(image_bytes, x, y, width, height):
    """Crop vehicle region from full frame using pixel bbox."""
    bgr, w, h = _decode_image(image_bytes)
    if bgr is None:
        return image_bytes
    x1 = max(0, int(x))
    y1 = max(0, int(y))
    x2 = min(w, int(x + width))
    y2 = min(h, int(y + height))
    if x2 <= x1 or y2 <= y1:
        return image_bytes
    crop = bgr[y1:y2, x1:x2]
    ok, buf = cv2.imencode(".jpg", crop)
    return buf.tobytes() if ok else image_bytes
