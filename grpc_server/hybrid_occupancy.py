"""
Hibrit doluluk: admin kalibrasyon poligonları (sabit) + yolov8n araç tespiti.
Araç alt-orta noktası (cx, y2) poligon içinde mi? — parkomfy-backend ile aynı mantık.
Custom Bos/Dolu modeli doluluk için KULLANILMAZ (düşük accuracy).
"""
import logging

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


def analyze_calibrated_area(image_bytes, area_id):
    """
    Returns list of dicts sorted by slot_number:
      slot_number, corners (norm), occupied, confidence,
      vehicle_x/y/width/height (norm bbox of matched vehicle)
    """
    calib = slot_model.load_calibration(area_id)
    if not calib:
        return []

    bgr, w, h = cv_engine._decode_image(image_bytes)
    if bgr is None:
        return []

    detections = cv_engine.detect_vehicles_yolo(bgr)
    slots_cfg = sorted(calib.get("slots") or [], key=lambda s: int(s.get("slot_number", 0)))

    results = []
    for slot_entry in slots_cfg:
        corners = slot_entry.get("corners") or []
        if len(corners) != 4:
            continue
        slot_number = int(slot_entry.get("slot_number", len(results) + 1))
        corners_norm = [(float(c[0]), float(c[1])) for c in corners]
        poly_px = [
            (int(corners_norm[i][0] * w), int(corners_norm[i][1] * h))
            for i in range(4)
        ]
        occupied, det = cv_engine.slot_occupied_by_point_polygon(poly_px, detections)
        conf = float(det["confidence"]) if det else 0.0
        vx, vy, vw, vh = _norm_bbox_from_det(det, w, h)
        x, y, bw, bh = _quad_bbox_norm(corners_norm)
        results.append({
            "slot_number": slot_number,
            "corners": corners_norm,
            "x": x,
            "y": y,
            "width": bw,
            "height": bh,
            "occupied": bool(occupied),
            "confidence": conf if occupied else 0.0,
            "vehicle_x": vx,
            "vehicle_y": vy,
            "vehicle_width": vw,
            "vehicle_height": vh,
        })

    dolu = sum(1 for r in results if r["occupied"])
    slot_bits = ", ".join(
        f"S{r['slot_number']}:{'DOLU' if r['occupied'] else 'BOS'}" for r in results
    )
    logger.info(
        "Hybrid [%s]: %s slot, %s dolu (yolov8n+poligon) [%s]",
        area_id, len(results), dolu, slot_bits,
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
