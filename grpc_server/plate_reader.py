"""
License plate OCR pipeline (prototype port).
ROI: bottom-center of vehicle crop -> resize -> grayscale -> bilateral -> adaptive threshold -> EasyOCR.
Post-process: difflib match against AUTHORIZED_DB; fallback YABANCI / TESPIT EDILEMEDI.
"""
import cv2
import difflib
import logging

logger = logging.getLogger(__name__)

AUTHORIZED_DB = ["34PGZ545", "67ACP810"]

_reader = None


def _get_reader():
    global _reader
    if _reader is None:
        import easyocr
        _reader = easyocr.Reader(["en"], gpu=True)
        logger.info("EasyOCR reader initialized")
    return _reader


def process_and_read(frame, slot_id=0):
    """Run full OCR pipeline on a vehicle crop (BGR). Returns plate text label."""
    if frame is None or frame.size == 0:
        logger.warning("[OCR] Slot %s: empty frame", slot_id)
        return "TESPIT EDILEMEDI"

    h, w = frame.shape[:2]
    roi = frame[int(h * 0.5) : int(h * 0.95), int(w * 0.1) : int(w * 0.9)]
    if roi.size == 0:
        return "TESPIT EDILEMEDI"

    zoom = cv2.resize(roi, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(zoom, cv2.COLOR_BGR2GRAY)
    distorted = cv2.bilateralFilter(gray, 11, 17, 17)
    thresh = cv2.adaptiveThreshold(
        distorted, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2
    )

    reader = _get_reader()
    results = reader.readtext(thresh, detail=1)
    best_match = "YABANCI"
    best_prob = 0.0

    if not results:
        return "TESPIT EDILEMEDI"

    for (_bbox, text, prob) in results:
        clean = text.upper().replace(" ", "").strip()
        if not clean:
            continue
        matches = difflib.get_close_matches(clean, AUTHORIZED_DB, n=1, cutoff=0.3)
        if matches:
            best_match = matches[0]
            best_prob = float(prob)
            break
        if prob > best_prob:
            best_prob = float(prob)

    return best_match


def plate_confidence(plate_text):
    """Map plate label to confidence score for gRPC response."""
    if plate_text in AUTHORIZED_DB:
        return 0.92
    if plate_text == "YABANCI":
        return 0.55
    return 0.0
