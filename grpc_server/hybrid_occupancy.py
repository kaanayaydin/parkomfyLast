"""
Hibrit doluluk: admin kalibrasyon poligonu + model tespiti (poligon icinde mi?).

yolov8n:  arac kutusu alt-orta noktasi admin poligonunda -> DOLU
best.pt:    Bos/Dolu kutusu alt-orta noktasi admin poligonunda -> BOS/DOLU
            (eslesme yoksa IoU, o da yoksa yolov8n yedek)

best.pt yuklu degilse dogrudan yolov8n kullanilir.
"""
import hashlib
import logging
import os
import time
from pathlib import Path

import cv2
import numpy as np

import cv_engine
import slot_model

logger = logging.getLogger(__name__)


def _norm_bbox_from_det(det, w, h):
    if not det or w <= 0 or h <= 0:
        return 0.0, 0.0, 0.0, 0.0
    x1, y1, x2, y2 = det["x1"], det["y1"], det["x2"], det["y2"]
    return (
        float(x1) / w,
        float(y1) / h,
        float(x2 - x1) / w,
        float(y2 - y1) / h,
    )


def _quad_bbox_norm(corners):
    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    return min_x, min_y, max_x - min_x, max_y - min_y


def _model_label():
    path = slot_model.get_model_path()
    if path and os.path.isfile(path):
        return Path(path).name
    return "best.pt"


def _analyze_yolov8n(quads_norm, bgr, w, h):
    """Fallback: yolov8n araç merkezi poligon içinde mi."""
    detections = cv_engine.detect_vehicles_yolo(bgr)
    out = []
    for corners_norm in quads_norm:
        poly_px = [
            (int(corners_norm[i][0] * w), int(corners_norm[i][1] * h))
            for i in range(4)
        ]
        occupied, det = cv_engine.slot_occupied_by_point_polygon(poly_px, detections)
        conf = float(det["confidence"]) if det else 0.0
        vx, vy, vw, vh = _norm_bbox_from_det(det, w, h)
        out.append({
            "occupied": bool(occupied),
            "confidence": conf if occupied else 0.0,
            "vehicle_x": vx,
            "vehicle_y": vy,
            "vehicle_width": vw,
            "vehicle_height": vh,
        })
    return out


_OCC_CACHE = {}
_CACHE_TTL_SEC = float(os.environ.get("PARKOMFY_OCC_CACHE_SEC", "1.5"))


def _frame_signature(image_bytes):
    if not image_bytes:
        return "empty"
    head = image_bytes[:1024]
    tail = image_bytes[-1024:] if len(image_bytes) > 1024 else b""
    return hashlib.md5(head + tail + str(len(image_bytes)).encode()).hexdigest()


def analyze_calibrated_area(image_bytes, area_id, use_cache=True):
    """
    Returns list of dicts sorted by slot_number:
      slot_number, corners (norm), occupied, confidence,
      vehicle_x/y/width/height (norm bbox of matched vehicle)
    """
    if use_cache and image_bytes:
        sig = _frame_signature(image_bytes)
        now = time.time()
        cached = _OCC_CACHE.get(area_id)
        if cached and now - cached[0] < _CACHE_TTL_SEC and cached[1] == sig:
            return cached[2]
        results = _analyze_calibrated_area_impl(image_bytes, area_id)
        _OCC_CACHE[area_id] = (now, sig, results)
        return results
    return _analyze_calibrated_area_impl(image_bytes, area_id)


def _analyze_calibrated_area_impl(image_bytes, area_id):
    calib = slot_model.load_calibration(area_id)
    if not calib:
        return []

    bgr, w, h = cv_engine._decode_image(image_bytes)
    if bgr is None:
        return []

    slots_cfg = sorted(calib.get("slots") or [], key=lambda s: int(s.get("slot_number", 0)))
    quads_norm = []
    slot_numbers = []
    for slot_entry in slots_cfg:
        corners = slot_entry.get("corners") or []
        if len(corners) != 4:
            continue
        slot_numbers.append(int(slot_entry.get("slot_number", len(slot_numbers) + 1)))
        quads_norm.append([(float(c[0]), float(c[1])) for c in corners])

    if not quads_norm:
        return []

    use_custom = slot_model._load_slot_model() is not None
    if use_custom:
        occ_items = slot_model.check_occupancy_with_model(image_bytes, quads_norm)
        engine = _model_label()
    else:
        occ_items = _analyze_yolov8n(quads_norm, bgr, w, h)
        engine = "yolov8n"

    results = []
    for idx, corners_norm in enumerate(quads_norm):
        occ_data = occ_items[idx] if idx < len(occ_items) else {}
        occupied = bool(occ_data.get("occupied", False))
        conf = float(occ_data.get("confidence", 0.0)) if occupied else 0.0
        x, y, bw, bh = _quad_bbox_norm(corners_norm)
        results.append({
            "slot_number": slot_numbers[idx],
            "corners": corners_norm,
            "x": x,
            "y": y,
            "width": bw,
            "height": bh,
            "occupied": occupied,
            "confidence": conf,
            "vehicle_x": float(occ_data.get("vehicle_x", 0.0)),
            "vehicle_y": float(occ_data.get("vehicle_y", 0.0)),
            "vehicle_width": float(occ_data.get("vehicle_width", 0.0)),
            "vehicle_height": float(occ_data.get("vehicle_height", 0.0)),
        })

    dolu = sum(1 for r in results if r["occupied"])
    slot_bits = ", ".join(
        f"S{r['slot_number']}:{'DOLU' if r['occupied'] else 'BOS'}" for r in results
    )
    logger.info(
        "Hybrid [%s]: %s slot, %s dolu (%s+kalibrasyon) [%s]",
        area_id, len(results), dolu, engine, slot_bits,
    )
    return results


def render_annotated_jpeg(image_bytes, area_id):
    """Yeşil/kırmızı poligon overlay."""
    bgr, w, h = cv_engine._decode_image(image_bytes)
    if bgr is None:
        return None
    for item in analyze_calibrated_area(image_bytes, area_id):
        corners = item["corners"]
        poly = np.array(
            [[int(c[0] * w), int(c[1] * h)] for c in corners],
            np.int32,
        ).reshape((-1, 1, 2))
        color = (0, 0, 255) if item["occupied"] else (0, 255, 0)
        cv2.polylines(bgr, [poly], True, color, 2)
        label = f"S{item['slot_number']} {'DOLU' if item['occupied'] else 'BOS'}"
        cv2.putText(
            bgr,
            label,
            (poly[0][0][0], max(12, poly[0][0][1] - 6)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            color,
            1,
            cv2.LINE_AA,
        )
    ok, buf = cv2.imencode(".jpg", bgr, [int(cv2.IMWRITE_JPEG_QUALITY), 82])
    return buf.tobytes() if ok else None
