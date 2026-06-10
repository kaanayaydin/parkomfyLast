"""
Custom YOLO parking slot model (best.pt) — layout prediction + occupancy hints.
"""
import io
import json
import logging
import os
from pathlib import Path

import cv2
import numpy as np

logger = logging.getLogger(__name__)

_BASE = Path(__file__).resolve().parent
_PROJECT_ROOT = _BASE.parent
_CALIB_DIR = _BASE / "calibrations"
_DEFAULT_MODEL = str(_PROJECT_ROOT / "best (2).pt")

_slot_model = None
_model_names = {}


def get_model_path():
    return os.environ.get("PARKOMFY_SLOT_MODEL", _DEFAULT_MODEL)


def _load_slot_model():
    global _slot_model, _model_names
    if _slot_model is not None:
        return _slot_model
    path = get_model_path()
    if not os.path.isfile(path):
        logger.warning("Slot model not found: %s", path)
        return None
    try:
        from ultralytics import YOLO
        _slot_model = YOLO(path)
        _model_names = _slot_model.names or {}
        logger.info("Parking slot model loaded: %s classes=%s", path, _model_names)
        return _slot_model
    except Exception as e:
        logger.error("Slot model load failed: %s", e)
        return None


_SLOT_CLASSES = {"bos", "dolu"}
_MIN_LAYOUT_CONF = float(os.environ.get("PARKOMFY_SLOT_MIN_CONF", "0.25"))


def _is_slot_layout_class(name):
    """Sadece park yeri sınıfları (Bos/Dolu); 'object' vb. atlanır."""
    n = (name or "").lower().strip()
    if n in _SLOT_CLASSES:
        return True
    if "bos" in n or "dolu" in n:
        return True
    if "empty" in n or "vacant" in n or "free" in n:
        return True
    if "occupied" in n or "full" in n:
        return True
    return False


def _norm_corners_from_xyxy(x1, y1, x2, y2, w, h):
    return _order_corners_norm([
        (x1 / w, y1 / h),
        (x2 / w, y1 / h),
        (x2 / w, y2 / h),
        (x1 / w, y2 / h),
    ])


def _order_corners_norm(corners):
    """Köşeleri TL → TR → BR → BL sırasına getir (poligon çizimi tutarlı olsun)."""
    pts = np.array(corners, dtype=np.float64)
    if len(pts) != 4:
        return [(float(c[0]), float(c[1])) for c in corners]
    center = pts.mean(axis=0)
    angles = np.arctan2(pts[:, 1] - center[1], pts[:, 0] - center[0])
    ordered = pts[np.argsort(angles)]
    idx = int(np.lexsort((ordered[:, 0], ordered[:, 1]))[0])
    ordered = np.roll(ordered, -idx, axis=0)
    return [(float(p[0]), float(p[1])) for p in ordered]


def _nms_layout_predictions(predictions, iou_thresh=0.42):
    """Çakışan tahminleri birleştir — aynı slota birden fazla kutu gelmesin."""
    preds = sorted(predictions, key=lambda p: p["confidence"], reverse=True)
    kept = []
    for p in preds:
        if any(_iou_quads(p["corners"], k["corners"]) >= iou_thresh for k in kept):
            continue
        kept.append(p)
    return kept


def _occupied_from_class_name(name):
    n = (name or "").lower()
    if any(k in n for k in ("occupied", "dolu", "full", "car_in")):
        return True
    if any(k in n for k in ("empty", "bos", "free", "available", "vacant")):
        return False
    return None


def predict_slot_layout(image_bytes, conf=None):
    if conf is None:
        conf = _MIN_LAYOUT_CONF
    """
    Returns list of dicts:
      corners: [(x,y) normalized 0-1] x4
      occupied: bool|None
      confidence: float
      class_name: str
    """
    model = _load_slot_model()
    if model is None:
        return [], 0, 0

    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if bgr is None:
        return [], 0, 0
    h, w = bgr.shape[:2]

    results = model.predict(bgr, conf=conf, verbose=False)
    if not results:
        return [], w, h
    r0 = results[0]
    out = []

    # OBB (oriented bounding box) — preferred for parking slots
    if hasattr(r0, "obb") and r0.obb is not None and len(r0.obb) > 0:
        xyxyxyxy = r0.obb.xyxyxyxy.cpu().numpy()
        confs = r0.obb.conf.cpu().numpy()
        clss = r0.obb.cls.cpu().numpy()
        for i in range(len(xyxyxyxy)):
            pts = xyxyxyxy[i].reshape(4, 2)
            corners = _order_corners_norm(
                [(float(pts[j][0] / w), float(pts[j][1] / h)) for j in range(4)]
            )
            cid = int(clss[i])
            cname = _model_names.get(cid, str(cid))
            if not _is_slot_layout_class(cname):
                continue
            occ = _occupied_from_class_name(cname)
            out.append({
                "corners": corners,
                "occupied": occ if occ is not None else False,
                "confidence": float(confs[i]),
                "class_name": cname,
            })
        return _nms_layout_predictions(out), w, h

    # Standard detection boxes
    if r0.boxes is not None and len(r0.boxes) > 0:
        xyxy = r0.boxes.xyxy.cpu().numpy()
        confs = r0.boxes.conf.cpu().numpy()
        clss = r0.boxes.cls.cpu().numpy()
        for i in range(len(xyxy)):
            x1, y1, x2, y2 = xyxy[i][:4]
            corners = _norm_corners_from_xyxy(x1, y1, x2, y2, w, h)
            cid = int(clss[i])
            cname = _model_names.get(cid, str(cid))
            if not _is_slot_layout_class(cname):
                continue
            occ = _occupied_from_class_name(cname)
            out.append({
                "corners": corners,
                "occupied": occ if occ is not None else False,
                "confidence": float(confs[i]),
                "class_name": cname,
            })
    return _nms_layout_predictions(out), w, h


def load_calibration(area_id):
    """Load admin-confirmed slot polygons for an area."""
    if not area_id:
        return None
    path = _CALIB_DIR / f"{area_id}.json"
    if not path.is_file():
        return None
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        logger.warning("Calibration load failed %s: %s", path, e)
        return None


def save_calibration(area_id, slots, image_width=0, image_height=0):
    _CALIB_DIR.mkdir(parents=True, exist_ok=True)
    path = _CALIB_DIR / f"{area_id}.json"
    payload = {
        "area_id": area_id,
        "image_width": image_width,
        "image_height": image_height,
        "slots": slots,
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
    logger.info("Calibration saved: %s (%s slots)", path, len(slots))
    return str(path)


def calibrated_quads_norm(calibration):
    """Extract normalized quads from calibration JSON."""
    if not calibration:
        return []
    quads = []
    for s in calibration.get("slots", []):
        corners = s.get("corners") or []
        if len(corners) == 4:
            quads.append([(float(c[0]), float(c[1])) for c in corners])
    return quads


def _bottom_center_px(corners_norm, w, h):
    """yolov8n ile ayni: kutunun alt-orta noktasi (piksel)."""
    xs = [c[0] * w for c in corners_norm]
    ys = [c[1] * h for c in corners_norm]
    return int(sum(xs) / len(xs)), int(max(ys))


def _bbox_norm_from_corners(corners_norm):
    xs = [c[0] for c in corners_norm]
    ys = [c[1] for c in corners_norm]
    vx, vy = min(xs), min(ys)
    return vx, vy, max(xs) - vx, max(ys) - vy


def _bestpt_point_in_polygon(quad_norm, predictions, w, h):
    """
    yolov8n mantigi: best.pt tespitinin alt-orta noktasi admin poligonunda mi?
    Poligonda Dolu varsa -> dolu; sadece Bos varsa -> bos.
    """
    if w <= 0 or h <= 0:
        return None
    poly = np.array(
        [[int(c[0] * w), int(c[1] * h)] for c in quad_norm],
        np.int32,
    )
    in_poly = []
    for pred in predictions:
        cx, cy = _bottom_center_px(pred["corners"], w, h)
        if cv2.pointPolygonTest(poly, (cx, cy), False) >= 0:
            in_poly.append(pred)
    if not in_poly:
        return None
    dolu = [p for p in in_poly if p.get("occupied")]
    if dolu:
        best = max(dolu, key=lambda p: float(p.get("confidence", 0)))
        return True, float(best["confidence"]), best
    return False, 0.0, max(in_poly, key=lambda p: float(p.get("confidence", 0)))


def check_occupancy_with_model(image_bytes, quads_norm):
    """
    best.pt ile yolov8n ile ayni hibrit mantik:
      1) best.pt tespiti admin poligon icinde mi (alt-orta nokta)
      2) yoksa IoU eslesmesi
      3) yoksa yolov8n arac + poligon (yedek)
    """
    predictions, w, h = predict_slot_layout(image_bytes)
    results = []
    for quad in quads_norm:
        best_occ = False
        best_conf = 0.0
        best_pred = None

        poly_match = _bestpt_point_in_polygon(quad, predictions, w, h)
        if poly_match is not None:
            best_occ, best_conf, best_pred = poly_match
        else:
            best_iou = 0.0
            for pred in predictions:
                iou = _iou_quads(quad, pred["corners"])
                if iou > best_iou:
                    best_iou = iou
                    best_occ = pred.get("occupied", False)
                    best_conf = pred.get("confidence", 0.0)
                    best_pred = pred
            if best_iou < 0.3:
                best_occ, best_conf = _vehicle_in_quad(image_bytes, quad)
                best_pred = None

        vx = vy = vw = vh = 0.0
        if best_pred is not None:
            vx, vy, vw, vh = _bbox_norm_from_corners(best_pred["corners"])

        results.append({
            "corners": quad,
            "occupied": best_occ,
            "confidence": best_conf if best_occ else 0.0,
            "vehicle_x": vx,
            "vehicle_y": vy,
            "vehicle_width": vw,
            "vehicle_height": vh,
        })
    return results


def _vehicle_in_quad(image_bytes, quad_norm):
    try:
        import cv_engine
        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if bgr is None:
            return False, 0.0
        ih, iw = bgr.shape[:2]
        poly = np.array([[int(c[0] * iw), int(c[1] * ih)] for c in quad_norm], np.int32)
        dets = cv_engine.detect_vehicles_yolo(bgr)
        for det in dets:
            cx = (det["x1"] + det["x2"]) // 2
            cy = det["y2"]
            if cv2.pointPolygonTest(poly, (cx, cy), False) >= 0:
                return True, det.get("confidence", 0.5)
    except Exception:
        pass
    return False, 0.0


def _iou_quads(a, b):
    """Approximate IoU using bbox of quads."""
    def bbox(q):
        xs = [p[0] for p in q]
        ys = [p[1] for p in q]
        return min(xs), min(ys), max(xs), max(ys)

    ax1, ay1, ax2, ay2 = bbox(a)
    bx1, by1, bx2, by2 = bbox(b)
    ix1, iy1 = max(ax1, bx1), max(ay1, by1)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    if ix2 <= ix1 or iy2 <= iy1:
        return 0.0
    inter = (ix2 - ix1) * (iy2 - iy1)
    area_a = (ax2 - ax1) * (ay2 - ay1)
    area_b = (bx2 - bx1) * (by2 - by1)
    union = area_a + area_b - inter
    return inter / union if union > 0 else 0.0
