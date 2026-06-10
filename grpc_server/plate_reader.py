"""
Turkish license plate OCR pipeline.
Detect plate region (YOLO model or contour heuristic) -> preprocess -> EasyOCR ->
format validation and confusion correction.
"""
import logging
import os
import re
from pathlib import Path

import cv2
import numpy as np

logger = logging.getLogger(__name__)

FAIL_TEXT = "TESPIT EDILEMEDI"
# Turkish plate: 2 digits + 1-3 letters + 2-4 digits (e.g. 34ABC123, 06A1234)
TR_PLATE_RE = re.compile(r"^(\d{2})([A-Z]{1,3})(\d{2,4})$")

_reader = None
_paddle = None
_paddle_failed = False
_plate_yolo = None
_last_confidence = 0.0
_last_location_norm = None  # (x, y, w, h) normalized 0-1

# OCR backend: "paddle" (default, more accurate) or "easyocr" (fallback).
_OCR_BACKEND = os.environ.get("PARKOMFY_OCR_BACKEND", "paddle").strip().lower()
# Cap frame width before OCR to bound latency on high-res cameras. 1280 keeps
# plates readable while ~35% faster than full 1080p; smaller drops accuracy.
try:
    _OCR_MAX_WIDTH = int(os.environ.get("PARKOMFY_OCR_MAX_WIDTH", "1280"))
except ValueError:
    _OCR_MAX_WIDTH = 1280

# Position-aware confusion pairs: (wrong, right) applied in letter vs digit segments
_LETTER_FIXES = {"0": "O", "1": "I", "8": "B", "5": "S", "6": "G", "2": "Z"}
_DIGIT_FIXES = {"O": "0", "I": "1", "B": "8", "S": "5", "G": "6", "Z": "2", "D": "0", "Q": "0"}


def _gpu_available() -> bool:
    try:
        import torch
        return bool(torch.cuda.is_available())
    except Exception:
        return False


def _get_reader():
    global _reader
    if _reader is None:
        import easyocr
        use_gpu = _gpu_available()
        _reader = easyocr.Reader(["en"], gpu=use_gpu)
        logger.info("EasyOCR initialized (gpu=%s)", use_gpu)
    return _reader


def _get_paddle():
    """Lazy-init PaddleOCR with lightweight mobile models (CPU-friendly, ~200ms)."""
    global _paddle, _paddle_failed
    if _paddle is not None or _paddle_failed:
        return _paddle
    try:
        from paddleocr import PaddleOCR
        _paddle = PaddleOCR(
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            lang="en",
            text_detection_model_name="PP-OCRv5_mobile_det",
            text_recognition_model_name="en_PP-OCRv5_mobile_rec",
        )
        logger.info("PaddleOCR initialized (mobile det+rec)")
    except Exception as e:
        _paddle_failed = True
        logger.warning("PaddleOCR unavailable, falling back to EasyOCR: %s", e)
        _paddle = None
    return _paddle


def _paddle_read(frame) -> list:
    """Run PaddleOCR on a frame. Returns list of (text, score, poly)."""
    ocr = _get_paddle()
    if ocr is None:
        return []
    try:
        results = ocr.predict(frame)
    except Exception as e:
        logger.debug("PaddleOCR predict failed: %s", e)
        return []
    out = []
    for r in results or []:
        try:
            texts = r.get("rec_texts", [])
            scores = r.get("rec_scores", [])
            polys = r.get("rec_polys", r.get("dt_polys", []))
        except AttributeError:
            continue
        for i, text in enumerate(texts):
            score = float(scores[i]) if i < len(scores) else 0.0
            poly = polys[i] if i < len(polys) else None
            out.append((text, score, poly))
    return out


def _poly_to_norm(poly, w, h):
    """Convert a 4-point polygon to normalized (x, y, w, h)."""
    if poly is None or w <= 0 or h <= 0:
        return None
    xs = [float(p[0]) for p in poly]
    ys = [float(p[1]) for p in poly]
    x1, y1, x2, y2 = min(xs), min(ys), max(xs), max(ys)
    return (x1 / w, y1 / h, (x2 - x1) / w, (y2 - y1) / h)


def _paddle_pipeline(frame, slot_id=0):
    """PaddleOCR-based plate read. Returns (text, confidence, location_norm) or None."""
    h, w = frame.shape[:2]
    # Downscale wide frames; normalized location stays resolution-independent.
    if _OCR_MAX_WIDTH > 0 and w > _OCR_MAX_WIDTH:
        scale = _OCR_MAX_WIDTH / float(w)
        frame = cv2.resize(frame, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
        h, w = frame.shape[:2]
    candidates = _paddle_read(frame)
    if not candidates:
        return None
    best = None  # (valid_flag, score), text, location
    for text, score, poly in candidates:
        valid = validate_turkish_plate(text)
        clean = validate_turkish_plate(text) or _normalize_raw(text)
        if not clean:
            continue
        rank = (1 if valid else 0, score)
        if best is None or rank > best[0]:
            best = (rank, valid if valid else clean, _poly_to_norm(poly, w, h), bool(valid))
    if best is None:
        return None
    _, text, location, is_valid = best
    score = best[0][1]
    if is_valid:
        return text, min(0.99, score), location
    return FAIL_TEXT, score * 0.5, location


def _get_plate_yolo():
    global _plate_yolo
    if _plate_yolo is not None:
        return _plate_yolo
    model_path = os.environ.get("PARKOMFY_PLATE_MODEL", "").strip()
    if not model_path:
        for candidate in ("plate_yolo.pt", "best_plate.pt"):
            p = Path(__file__).resolve().parent / candidate
            if p.is_file():
                model_path = str(p)
                break
    if not model_path or not os.path.isfile(model_path):
        return None
    try:
        from ultralytics import YOLO
        _plate_yolo = YOLO(model_path)
        logger.info("Plate YOLO loaded: %s", model_path)
        return _plate_yolo
    except Exception as e:
        logger.warning("Plate YOLO load failed: %s", e)
        return None


def _normalize_raw(text: str) -> str:
    if not text:
        return ""
    return re.sub(r"[^A-Z0-9]", "", text.upper())


def _fix_confusions(plate: str) -> str:
    """Apply position-aware O/0 style fixes for Turkish plate segments."""
    m = TR_PLATE_RE.match(plate)
    if not m:
        return plate
    city, letters, digits = m.group(1), m.group(2), m.group(3)
    fixed_letters = "".join(_LETTER_FIXES.get(c, c) for c in letters)
    fixed_digits = "".join(_DIGIT_FIXES.get(c, c) for c in digits)
    return f"{city}{fixed_letters}{fixed_digits}"


def _try_fix_to_valid(plate: str) -> str:
    """Attempt to coerce OCR output into valid TR plate format."""
    clean = _normalize_raw(plate)
    if not clean:
        return ""
    if TR_PLATE_RE.match(clean):
        return _fix_confusions(clean)
    # Heuristic: find 2-digit city + letters + digits pattern inside string
    m = re.search(r"(\d{2})([A-Z0-9]{1,4})(\d{2,4})", clean)
    if m:
        candidate = _fix_confusions(f"{m.group(1)}{m.group(2)}{m.group(3)}")
        if TR_PLATE_RE.match(candidate):
            return candidate
    return clean if TR_PLATE_RE.match(_fix_confusions(clean)) else ""


def validate_turkish_plate(text: str) -> str:
    """Return normalized valid plate or empty string."""
    fixed = _try_fix_to_valid(text)
    return fixed if fixed and TR_PLATE_RE.match(fixed) else ""


def _detect_plate_yolo(frame) -> tuple:
    """Return (x, y, w, h) pixel bbox or None."""
    model = _get_plate_yolo()
    if model is None:
        return None
    try:
        results = model(frame, verbose=False)[0]
        best = None
        best_conf = 0.0
        for box in results.boxes:
            conf = float(box.conf[0])
            if conf < 0.25 or conf <= best_conf:
                continue
            xyxy = box.xyxy[0].cpu().numpy()
            x1, y1, x2, y2 = int(xyxy[0]), int(xyxy[1]), int(xyxy[2]), int(xyxy[3])
            best = (x1, y1, x2 - x1, y2 - y1)
            best_conf = conf
        return best
    except Exception as e:
        logger.debug("Plate YOLO detect failed: %s", e)
        return None


def _detect_plate_contour(frame) -> tuple:
    """Heuristic plate region via edges + aspect ratio in lower frame."""
    h, w = frame.shape[:2]
    roi = frame[int(h * 0.35): int(h * 0.98), int(w * 0.05): int(w * 0.95)]
    if roi.size == 0:
        return None
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    gray = cv2.bilateralFilter(gray, 9, 75, 75)
    edges = cv2.Canny(gray, 50, 150)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    best = None
    best_score = 0.0
    roi_h, roi_w = roi.shape[:2]
    for cnt in contours:
        x, y, cw, ch = cv2.boundingRect(cnt)
        if cw < 40 or ch < 10:
            continue
        aspect = cw / float(ch)
        if aspect < 2.0 or aspect > 6.5:
            continue
        area = cw * ch
        score = area * min(aspect, 5.0)
        if score > best_score:
            best_score = score
            best = (x, y, cw, ch)
    if best is None:
        return None
    bx, by, bw, bh = best
    return (bx + int(w * 0.05), by + int(h * 0.35), bw, bh)


def _detect_plate_region(frame) -> tuple:
    """Return pixel bbox (x, y, w, h) for plate region."""
    bbox = _detect_plate_yolo(frame)
    if bbox is not None:
        return bbox
    return _detect_plate_contour(frame)


def _preprocess_plate_crop(crop) -> list:
    """Return list of preprocessed images for OCR voting."""
    if crop is None or crop.size == 0:
        return []
    h, w = crop.shape[:2]
    scale = max(3.0, 300.0 / max(w, 1))
    zoom = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(zoom, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    blurred = cv2.bilateralFilter(enhanced, 9, 75, 75)
    _, otsu = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    adapt = cv2.adaptiveThreshold(
        blurred, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 15, 4
    )
    return [enhanced, otsu, adapt]


def _ocr_single(img) -> tuple:
    """Run EasyOCR on one preprocessed image. Returns (text, confidence)."""
    reader = _get_reader()
    try:
        results = reader.readtext(img, detail=1, allowlist="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
    except TypeError:
        results = reader.readtext(img, detail=1)
    best_text = ""
    best_conf = 0.0
    for (_bbox, text, prob) in results:
        clean = _normalize_raw(text)
        if not clean:
            continue
        conf = float(prob)
        valid = validate_turkish_plate(clean)
        score = conf + (0.15 if valid else 0.0)
        if score > best_conf:
            best_conf = conf
            best_text = valid if valid else clean
    return best_text, best_conf


def _ocr_plate_crop(crop) -> tuple:
    """Multi-preprocess OCR with voting. Returns (plate_text, confidence)."""
    variants = _preprocess_plate_crop(crop)
    if not variants:
        return FAIL_TEXT, 0.0
    votes = {}
    for img in variants:
        text, conf = _ocr_single(img)
        if not text or text == FAIL_TEXT:
            continue
        valid = validate_turkish_plate(text)
        key = valid if valid else text
        if key not in votes:
            votes[key] = {"count": 0, "conf_sum": 0.0, "valid": bool(valid)}
        votes[key]["count"] += 1
        votes[key]["conf_sum"] += conf
    if not votes:
        return FAIL_TEXT, 0.0
    best_key = max(votes.keys(), key=lambda k: (votes[k]["valid"], votes[k]["count"], votes[k]["conf_sum"]))
    info = votes[best_key]
    avg_conf = info["conf_sum"] / max(info["count"], 1)
    final = validate_turkish_plate(best_key) or best_key
    if not validate_turkish_plate(final):
        return FAIL_TEXT, avg_conf * 0.5
    return final, min(0.99, avg_conf)


def process_and_read_detailed(frame, slot_id=0) -> dict:
    """
    Full pipeline. Returns dict:
      text, confidence, location (x,y,w,h normalized), raw_text
    """
    global _last_confidence, _last_location_norm
    _last_confidence = 0.0
    _last_location_norm = None

    if frame is None or frame.size == 0:
        logger.warning("[OCR] Slot %s: empty frame", slot_id)
        return {"text": FAIL_TEXT, "confidence": 0.0, "location": None, "raw_text": ""}

    # Primary backend: PaddleOCR (more accurate, reads full plate string directly).
    if _OCR_BACKEND == "paddle":
        paddle_result = _paddle_pipeline(frame, slot_id)
        if paddle_result is not None:
            text, conf, location = paddle_result
            _last_confidence = conf
            _last_location_norm = location
            logger.info("[OCR/paddle] Slot %s -> %s (%.2f)", slot_id, text, conf)
            return {
                "text": text,
                "confidence": conf,
                "location": location,
                "raw_text": text,
            }

    # Fallback: EasyOCR + heuristic plate localization.
    h, w = frame.shape[:2]
    bbox = _detect_plate_region(frame)
    if bbox is None:
        # Fallback: bottom-center ROI (entrance close-up cameras)
        crop = frame[int(h * 0.4): int(h * 0.95), int(w * 0.1): int(w * 0.9)]
        _last_location_norm = (0.1, 0.4, 0.8, 0.55)
    else:
        x, y, bw, bh = bbox
        pad_x = int(bw * 0.05)
        pad_y = int(bh * 0.1)
        x1 = max(0, x - pad_x)
        y1 = max(0, y - pad_y)
        x2 = min(w, x + bw + pad_x)
        y2 = min(h, y + bh + pad_y)
        crop = frame[y1:y2, x1:x2]
        _last_location_norm = (x1 / w, y1 / h, (x2 - x1) / w, (y2 - y1) / h)

    text, conf = _ocr_plate_crop(crop)
    _last_confidence = conf
    logger.info("[OCR/easyocr] Slot %s -> %s (%.2f)", slot_id, text, conf)
    return {
        "text": text,
        "confidence": conf,
        "location": _last_location_norm,
        "raw_text": text,
    }


def process_and_read(frame, slot_id=0):
    """Backward-compatible: returns plate text only."""
    return process_and_read_detailed(frame, slot_id).get("text", FAIL_TEXT)


def plate_confidence(plate_text):
    """Return last OCR confidence for valid plates, else 0."""
    if plate_text and plate_text != FAIL_TEXT and validate_turkish_plate(plate_text):
        return _last_confidence if _last_confidence > 0 else 0.75
    return 0.0


def get_last_location_norm():
    """Normalized plate bbox from last process_and_read_detailed call."""
    return _last_location_norm
