#!/usr/bin/env python3
"""
PARKOMFY YOLO gRPC Sunucusu (detection.proto uyumlu)
Port: 50051
Yer çizgisi tabanlı slot tespiti: HSV maske (beyaz/sarı) -> Kuş bakışı (homografi) -> Canny + HoughLinesP
-> Çizgi kesişimlerinden poligonlar. IoU ile dolu/boş. Saf OpenCV/geometrik dönüşüm.
"""
import io
import logging
import os
import threading
from concurrent.futures import ThreadPoolExecutor

import grpc
import detection_pb2
import detection_pb2_grpc

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

PORT = 50051

CAR_CLASS_ID = 2
MIN_CONFIDENCE = 0.25
IOU_OCCUPIED_THRESHOLD = 0.5

# Kuş bakışı çıktı boyutu
BIRDSEYE_W = 500
BIRDSEYE_H = 700

# Statik poligon / çizgi tespiti (eski)
USE_LINE_DETECTION = False

# Kaçış Noktası (Vanishing Point) + yapısal ızgara: perspektife uyumlu slotlar, araç altı çizgi kapanması çözülür.
USE_VANISHING_POINT = True
VP_MIN_LINES = 2
VP_MAX_SLOTS = 24

# ROI: Sadece görüntünün alt yarısı (zemin). VP hesaplandıktan sonra dinamik olarak VP altı kullanılabilir.
ROI_TOP_RATIO = 0.50
ROI_BOTTOM_RATIO = 0.96

# Izgarayı en belirgin 2-3 boyalı çizgiyle hizala (en uzun çizgilerin açıları = radyal ana yönler).
N_ANCHOR_LINES = 3
# findContours + approxPolyDP ile semantik çizgi poligonları; ızgara köşeleri boyalı şeride snap.
USE_SEMANTIC_SNAP = True
SEMANTIC_SNAP_MAX_DIST_NORM = 0.045
# Sütun sayısı: sabit değil, yerdeki boyalı çizgi sayısına göre (her çizgi = sınır).
USE_LINE_DRIVEN_COLUMNS = True
MIN_COLUMNS = 1
MAX_COLUMNS = 10
# Slot çerçevesi titreşim azaltma: EMA (Exponential Moving Average).
USE_SLOT_STABILIZER = True
STABILIZER_ALPHA = 0.35  # yeni = alpha*mevcut + (1-alpha)*önceki
# VP/çizgi bulunamadığında kullanılacak bölme: 2 dikine çizgi -> 3 bölme (1 satır x 3 sütun).
FALLBACK_GRID_ROWS = 1
FALLBACK_GRID_COLS = 3

_yolo_model = None
_slot_stabilizer_cache = {}


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


def _detect_cars_yolo(image_bytes):
    """YOLOv8 ile sadece 'car' sınıfı; normalize (0-1) x,y,w,h ve confidence."""
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
        for box in results.boxes:
            cls_id = int(box.cls[0])
            if cls_id != CAR_CLASS_ID:
                continue
            conf = float(box.conf[0])
            if conf < MIN_CONFIDENCE:
                continue
            xyxy = box.xyxy[0].cpu().numpy()
            x1, y1, x2, y2 = xyxy[0], xyxy[1], xyxy[2], xyxy[3]
            xn, yn = x1 / w, y1 / h
            wn, hn = (x2 - x1) / w, (y2 - y1) / h
            name = results.names.get(cls_id, "car")
            boxes_out.append(detection_pb2.BoundingBox(
                x=float(xn), y=float(yn), width=float(wn), height=float(hn),
                class_name=name, confidence=conf
            ))
        avg_conf = (sum(b.confidence for b in boxes_out) / len(boxes_out)) if boxes_out else 0.0
        logger.info("YOLOv8 (car): %s tespit, ortalama güven=%.2f", len(boxes_out), avg_conf)
        return boxes_out, len(boxes_out) > 0, avg_conf
    except Exception as e:
        logger.exception("YOLOv8 inference hatası: %s", e)
        return [], False, 0.0


def _detect_vehicles_simulation(image_data):
    """Simülasyon: araç kutuları (gerçek tespit yok)."""
    if len(image_data) < 500:
        return [], False, 0.25
    boxes = [
        detection_pb2.BoundingBox(x=0.05, y=0.55, width=0.2, height=0.25, class_name="car", confidence=0.88),
        detection_pb2.BoundingBox(x=0.28, y=0.55, width=0.2, height=0.25, class_name="car", confidence=0.85),
        detection_pb2.BoundingBox(x=0.51, y=0.55, width=0.2, height=0.25, class_name="car", confidence=0.82),
        detection_pb2.BoundingBox(x=0.74, y=0.55, width=0.2, height=0.25, class_name="car", confidence=0.80),
    ]
    return boxes, True, 0.84


# Eski DetectVehicles API uyumluluğu (car + bus + truck)
VEHICLE_CLASS_IDS = {2, 5, 7}


def _detect_vehicles_yolo(image_bytes):
    """Geriye uyum: car, bus, truck (DetectVehicles RPC)."""
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
        for box in results.boxes:
            cls_id = int(box.cls[0])
            if cls_id not in VEHICLE_CLASS_IDS:
                continue
            conf = float(box.conf[0])
            if conf < MIN_CONFIDENCE:
                continue
            xyxy = box.xyxy[0].cpu().numpy()
            x1, y1, x2, y2 = xyxy[0], xyxy[1], xyxy[2], xyxy[3]
            xn, yn = x1 / w, y1 / h
            wn, hn = (x2 - x1) / w, (y2 - y1) / h
            name = results.names.get(cls_id, "vehicle")
            boxes_out.append(detection_pb2.BoundingBox(
                x=float(xn), y=float(yn), width=float(wn), height=float(hn),
                class_name=name, confidence=conf
            ))
        avg_conf = (sum(b.confidence for b in boxes_out) / len(boxes_out)) if boxes_out else 0.0
        return boxes_out, len(boxes_out) > 0, avg_conf
    except Exception as e:
        logger.exception("YOLOv8 inference hatası: %s", e)
        return [], False, 0.0


# --- Poligon ROI & IoU (çizgi tespiti yok, sadece overlap) ---

SLOT_GRID_ROWS = 2
SLOT_GRID_COLS = 4


def _point_in_polygon(px, py, corners):
    """Point-in-polygon (ray casting). corners: list of (x,y) normalize 0-1."""
    n = len(corners)
    if n < 3:
        return False
    inside = False
    j = n - 1
    for i in range(n):
        xi, yi = corners[i][0], corners[i][1]
        xj, yj = corners[j][0], corners[j][1]
        if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi + 1e-10) + xi):
            inside = not inside
        j = i
    return inside


def _bbox_to_polygon(b):
    """BoundingBox (normalize) -> 4 köşe [(x,y), ...]."""
    return [
        (b.x, b.y),
        (b.x + b.width, b.y),
        (b.x + b.width, b.y + b.height),
        (b.x, b.y + b.height),
    ]


def _polygon_area(corners):
    """Shoelace; corners list of (x,y)."""
    n = len(corners)
    if n < 3:
        return 0.0
    area = 0.0
    for i in range(n):
        j = (i + 1) % n
        area += corners[i][0] * corners[j][1] - corners[j][0] * corners[i][1]
    return abs(area) / 2.0


def _iou_polygon_bbox(slot_corners, bbox):
    """
    Slot poligonu ile araç bbox kesişim alanı / birleşim alanı.
    IoU > IOU_OCCUPIED_THRESHOLD ise slot dolu sayılır.
    Shapely TopologyException (geçersiz/self-intersecting poligon) durumunda merkez-in-poligon fallback.
    """
    try:
        from shapely.geometry import Polygon
        poly_slot = Polygon(slot_corners)
        poly_bbox = Polygon(_bbox_to_polygon(bbox))
        if poly_slot.is_empty or poly_bbox.is_empty:
            return 0.0
        try:
            inter = poly_slot.intersection(poly_bbox).area
            union = poly_slot.union(poly_bbox).area
        except Exception:
            # GEOSException/TopologyException (invalid/self-intersecting polygon) -> fallback
            return _iou_polygon_bbox_fallback(slot_corners, bbox)
        if union <= 0:
            return 0.0
        return inter / union
    except ImportError:
        return _iou_polygon_bbox_fallback(slot_corners, bbox)


def _iou_polygon_bbox_fallback(slot_corners, bbox):
    """Shapely kullanılamadığında veya geçersiz geometri hatasında: merkez-in-poligon + alan oranı."""
    cx = bbox.x + bbox.width / 2.0
    cy = bbox.y + bbox.height / 2.0
    if not _point_in_polygon(cx, cy, slot_corners):
        return 0.0
    area_slot = _polygon_area(slot_corners)
    area_bbox = bbox.width * bbox.height
    if area_slot <= 0:
        return 0.0
    overlap_ratio = min(area_bbox, area_slot) / max(area_bbox, area_slot)
    return 0.6 if overlap_ratio > 0.3 else 0.0


def _quad_bbox(corners):
    """4 köşeden min x, min y, width, height (normalize)."""
    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    return min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)


def _perspective_grid_quads(img_w, img_h, rows, cols):
    """
    Statik poligon: Sabit 4 köşe (ROI = alt yarı zemin). Perspektif trapezoid ızgara.
    """
    top_y = ROI_TOP_RATIO
    bottom_y = ROI_BOTTOM_RATIO
    tl = (0.12, top_y)
    tr = (0.88, top_y)
    br = (0.96, bottom_y)
    bl = (0.04, bottom_y)
    quads = []
    for row in range(rows):
        for col in range(cols):
            t0, t1 = row / rows, (row + 1) / rows
            s0, s1 = col / cols, (col + 1) / cols

            def interp(s, t):
                top_x = tl[0] + (tr[0] - tl[0]) * s
                top_y_ = tl[1] + (tr[1] - tl[1]) * s
                bot_x = bl[0] + (br[0] - bl[0]) * s
                bot_y_ = bl[1] + (br[1] - bl[1]) * s
                x = top_x + (bot_x - top_x) * t
                y = top_y_ + (bot_y_ - top_y_) * t
                return (x, y)

            quads.append([interp(s0, t0), interp(s1, t0), interp(s1, t1), interp(s0, t1)])
    return quads


# ========== Yer çizgisi tabanlı slot tespiti (HSV + Kuş bakışı + Canny/Hough) ==========

def _get_src_quad_pixel(img_w, img_h):
    """ROI: Sadece zemin (alt yarı). Sabit 4 köşe - tavan/duvar dışarıda."""
    top_y = int(ROI_TOP_RATIO * img_h)
    bottom_y = int(ROI_BOTTOM_RATIO * img_h)
    tl = (int(0.12 * img_w), top_y)
    tr = (int(0.88 * img_w), top_y)
    br = (int(0.96 * img_w), bottom_y)
    bl = (int(0.04 * img_w), bottom_y)
    return [tl, tr, br, bl]


def _line_mask_hsv(bgr):
    """
    Renk maskeleme: beyaz ve sarı boyalı çizgiler. HSV + cv2.inRange.
    Asfalt temizlenir, sadece çizgiler kalır.
    """
    import cv2
    import numpy as np
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    # Beyaz: düşük S, yüksek V (OpenCV H 0-180)
    low_white = np.array([0, 0, 200])
    high_white = np.array([180, 35, 255])
    mask_white = cv2.inRange(hsv, low_white, high_white)
    # Sarı: H ~15-40 (iç mekân/soluk sarı dahil), orta S/V
    low_yellow = np.array([15, 60, 60])
    high_yellow = np.array([40, 255, 255])
    mask_yellow = cv2.inRange(hsv, low_yellow, high_yellow)
    mask = cv2.bitwise_or(mask_white, mask_yellow)
    # Morfoloji: çizgileri birleştir, gürültüyü azalt
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
    mask = cv2.dilate(mask, kernel)
    mask = cv2.erode(mask, kernel)
    return mask


def _detect_lines_canny_hough(mask, min_line_length_ratio=0.12, max_line_gap_ratio=0.015):
    """
    Daha seçici Hough: uzun çizgiler, az gap. Gürültü (kısa/parçalı) elenir.
    """
    import cv2
    import numpy as np
    h, w = mask.shape[:2]
    edges = cv2.Canny(mask, 50, 150)
    min_len = int(min(w, h) * min_line_length_ratio)
    max_gap = int(min(w, h) * max_line_gap_ratio)
    lines = cv2.HoughLinesP(
        edges, rho=1, theta=np.pi / 180, threshold=40,
        minLineLength=max(min_len, 50), maxLineGap=max(max_gap, 8)
    )
    if lines is None:
        return []
    return [tuple(map(int, line[0])) for line in lines]


def _filter_lines_by_angle_and_length(lines, be_w, be_h, min_length_ratio=0.1, angle_tolerance_deg=15):
    """
    Sadece uzun çizgileri tut; açıya göre ~yatay ve ~dikey iki gruptan birine yakın olmalı.
    Yatay/dikey olmayan ve çok kısa gürültü elenir.
    """
    import numpy as np
    if not lines:
        return []
    min_len = min(be_w, be_h) * min_length_ratio
    filtered = []
    for (x1, y1, x2, y2) in lines:
        length = np.hypot(x2 - x1, y2 - y1)
        if length < min_len:
            continue
        angle = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        # Kuş bakışında slot çizgileri ~0° (yatay) veya ~90°/-90° (dikey)
        a = angle % 180
        if a <= angle_tolerance_deg or a >= 180 - angle_tolerance_deg:
            filtered.append((x1, y1, x2, y2))
        elif 90 - angle_tolerance_deg <= a <= 90 + angle_tolerance_deg:
            filtered.append((x1, y1, x2, y2))
    return filtered


def _line_intersection(l1, l2):
    """İki doğru parçası kesişim noktası (piksel). Kesim yoksa None."""
    x1, y1, x2, y2 = l1
    x3, y3, x4, y4 = l2
    den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if abs(den) < 1e-6:
        return None
    t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
    u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / den
    if 0 <= u <= 1:
        x = x3 + u * (x4 - x3)
        y = y3 + u * (y4 - y3)
        return (x, y)
    return None


def _cluster_lines_by_angle(lines, num_clusters=2):
    """Çizgileri açıya göre iki gruba ayır (kuş bakışında ~yatay ve ~dikey)."""
    import numpy as np
    if len(lines) < 2:
        return lines, [] if len(lines) < 2 else [lines]
    angles = []
    for (x1, y1, x2, y2) in lines:
        a = np.arctan2(y2 - y1, x2 - x1)
        angles.append((a, (x1, y1, x2, y2)))
    angles_np = np.array([a[0] for a in angles])
    # K-means benzeri: iki merkez (rastgele iki açı ile başla)
    c0 = angles_np[0]
    c1 = angles_np[len(angles_np) // 2] if len(angles_np) > 1 else c0 + 0.5
    for _ in range(15):
        g0 = [a for i, a in enumerate(angles) if abs(a[0] - c0) <= abs(a[0] - c1)]
        g1 = [a for i, a in enumerate(angles) if abs(a[0] - c0) > abs(a[0] - c1)]
        if not g0 or not g1:
            break
        c0 = np.mean([a[0] for a in g0])
        c1 = np.mean([a[0] for a in g1])
    grp0 = [a[1] for a in angles if abs(a[0] - c0) <= abs(a[0] - c1)]
    grp1 = [a[1] for a in angles if abs(a[0] - c0) > abs(a[0] - c1)]
    return grp0, grp1


def _form_quads_from_line_intersections(lines_grp0, lines_grp1, be_w, be_h, min_cell_area_ratio=0.001):
    """
    İki çizgi grubunun kesişimlerinden grid noktaları; ardından 4'lü hücreler (slot poligonları).
    Kuş bakışı piksel koordinatlarında quad listesi döner.
    """
    import numpy as np
    intersections = []
    for l0 in lines_grp0:
        for l1 in lines_grp1:
            p = _line_intersection(l0, l1)
            if p is None:
                continue
            x, y = p
            if 0 <= x <= be_w and 0 <= y <= be_h:
                intersections.append((x, y))
    if len(intersections) < 4:
        return []
    # Noktaları kümele (yakın noktaları tekilleştir)
    pts = np.array(intersections, dtype=np.float32)
    from collections import defaultdict
    cell_size = min(be_w, be_h) * 0.03
    grid = defaultdict(list)
    for (x, y) in pts:
        gx, gy = int(x / cell_size), int(y / cell_size)
        grid[(gx, gy)].append((x, y))
    unique = []
    for (gx, gy), lst in grid.items():
        if lst:
            unique.append((np.mean([p[0] for p in lst]), np.mean([p[1] for p in lst])))
    if len(unique) < 4:
        return []
    unique = np.array(unique)
    # X ve Y'ye göre sırala -> satır/sütun düzeni (yaklaşık)
    x_sort = np.argsort(unique[:, 0])
    y_sort = np.argsort(unique[:, 1])
    # Basit strateji: noktaları X sonra Y ile sırala, 2D grid varsay (satır_say, col_say tahmini)
    n_pts = len(unique)
    n_cols = max(2, int(np.sqrt(n_pts * (be_w / be_h))))
    n_rows = max(2, (n_pts + n_cols - 1) // n_cols)
    order_y = np.lexsort((unique[:, 0], unique[:, 1]))
    sorted_pts = unique[order_y]
    # Satırlara böl (Y'de sıçrama olan yerde satır değişir)
    rows = []
    row = [sorted_pts[0]]
    thresh_y = min(be_w, be_h) * 0.05
    for i in range(1, len(sorted_pts)):
        if sorted_pts[i][1] - sorted_pts[i - 1][1] > thresh_y:
            row.sort(key=lambda p: p[0])
            rows.append(row)
            row = [sorted_pts[i]]
        else:
            row.append(sorted_pts[i])
    if row:
        row.sort(key=lambda p: p[0])
        rows.append(row)
    quads = []
    min_area = be_w * be_h * min_cell_area_ratio
    for r in range(len(rows) - 1):
        for c in range(min(len(rows[r]) - 1, len(rows[r + 1]) - 1)):
            p00 = rows[r][c]
            p10 = rows[r][c + 1]
            p11 = rows[r + 1][c + 1]
            p01 = rows[r + 1][c]
            quad = [p00, p10, p11, p01]
            # Alan kontrolü
            area = 0.5 * abs(
                (p00[0] * p10[1] - p10[0] * p00[1]) +
                (p10[0] * p11[1] - p11[0] * p10[1]) +
                (p11[0] * p01[1] - p01[0] * p11[1]) +
                (p01[0] * p00[1] - p00[0] * p01[1])
            )
            if area >= min_area:
                quads.append(quad)
    return quads


def _birdseye_quads_to_original_normalized(quads_birdseye, M_inv, img_w, img_h):
    """
    Kuş bakışı piksel koordinatlarındaki quad'ları orijinal görüntüye (normalize 0-1) dönüştür.
    cv2.perspectiveTransform ile ters homografi.
    """
    import cv2
    import numpy as np
    out = []
    for quad in quads_birdseye:
        pts = np.array([[np.float32(q[0]), np.float32(q[1])] for q in quad], dtype=np.float32)
        pts = pts.reshape(-1, 1, 2)
        transformed = cv2.perspectiveTransform(pts, M_inv)
        normalized = [(float(p[0][0]) / img_w, float(p[0][1]) / img_h) for p in transformed]
        out.append(normalized)
    return out


def _slots_from_line_detection_pipeline(img_bgr, img_w, img_h):
    """
    ROI (alt yarı) + kuş bakışı + seçici Hough + açı/uzunluk filtresi.
    Çok gürültü toplarsa None döner; statik poligon kullanılır.
    """
    import cv2
    import numpy as np
    # ROI: _get_src_quad_pixel zaten alt yarı köşelerini kullanıyor (tavan/duvar dışarıda)
    src_quad = _get_src_quad_pixel(img_w, img_h)
    # src_quad zaten ROI ile uyumlu (top = ROI_TOP_RATIO)
    src_pts = np.array(src_quad, dtype=np.float32)
    dst_rect = np.array([
        [0, 0], [BIRDSEYE_W, 0], [BIRDSEYE_W, BIRDSEYE_H], [0, BIRDSEYE_H]
    ], dtype=np.float32)
    M = cv2.getPerspectiveTransform(src_pts, dst_rect)
    M_inv = cv2.getPerspectiveTransform(dst_rect, src_pts)
    birdseye = cv2.warpPerspective(img_bgr, M, (BIRDSEYE_W, BIRDSEYE_H))
    mask = _line_mask_hsv(birdseye)
    lines = _detect_lines_canny_hough(mask)
    lines = _filter_lines_by_angle_and_length(lines, BIRDSEYE_W, BIRDSEYE_H, min_length_ratio=0.12)
    if len(lines) < 6:
        logger.info("Çizgi tespiti: yetersiz çizgi (%s), statik poligon kullanılacak", len(lines))
        return None
    grp0, grp1 = _cluster_lines_by_angle(lines)
    if len(grp0) < 2 or len(grp1) < 2:
        logger.info("Çizgi grupları yetersiz, fallback kullanılacak")
        return None
    quads_be = _form_quads_from_line_intersections(grp0, grp1, BIRDSEYE_W, BIRDSEYE_H)
    if not quads_be:
        logger.info("Kesişimden quad üretilemedi, fallback kullanılacak")
        return None
    quads_norm = _birdseye_quads_to_original_normalized(quads_be, M_inv, img_w, img_h)
    logger.info("Yer çizgisi pipeline: %s slot poligonu (kuş bakışı + Hough)", len(quads_norm))
    return quads_norm


# ========== Kaçış Noktası (Vanishing Point) & Yapısal Izgara ==========

def _line_segment_to_abc(x1, y1, x2, y2):
    """Doğru parçası (x1,y1)-(x2,y2) -> ax+by+c=0 (a,b,c); normal vektör birimlenir."""
    import numpy as np
    a = float(y2 - y1)
    b = float(x1 - x2)
    c = float(x2 * y1 - x1 * y2)
    n = np.sqrt(a * a + b * b) + 1e-10
    return a / n, b / n, c / n


def _vanishing_point_least_squares(lines_abc):
    """
    Tüm doğruların (ax+by+c=0) mümkün olduğunca üzerinden geçtiği noktayı bul.
    En küçük kareler: min sum_i (a_i*x + b_i*y + c_i)^2 -> (x,y).
    """
    import numpy as np
    if len(lines_abc) < 2:
        return None
    A = np.array([[a, b] for a, b, _ in lines_abc], dtype=np.float64)
    b_vec = np.array([-c for _, _, c in lines_abc], dtype=np.float64)
    try:
        x, residuals, rank, s = np.linalg.lstsq(A, b_vec, rcond=None)
        if rank < 2:
            return None
        return (float(x[0]), float(x[1]))
    except Exception:
        return None


def _distance_point_to_line(px, py, x1, y1, x2, y2):
    """Nokta (px,py) ile doğru (x1,y1)-(x2,y2) arası dik mesafe (piksel)."""
    import numpy as np
    num = abs((y2 - y1) * px - (x2 - x1) * py + x2 * y1 - y2 * x1)
    den = np.hypot(y2 - y1, x2 - x1) + 1e-10
    return num / den


def _strong_lines_in_roi(img_bgr, img_w, img_h):
    """
    Zemin (ROI) üzerinde beyaz/sarı boyalı dikine çizgiler: HSV maske + HoughLinesP.
    Perspektifte dikeye yakın çizgiler tutulur; yatay gürültü atılır.
    """
    import cv2
    import numpy as np
    h, w = img_bgr.shape[0], img_bgr.shape[1]
    roi_top = int(ROI_TOP_RATIO * h)
    roi_bottom = int(ROI_BOTTOM_RATIO * h)
    roi_img = img_bgr[roi_top:roi_bottom, :].copy()
    mask = _line_mask_hsv(roi_img)
    roi_h, roi_w = roi_img.shape[0], roi_img.shape[1]
    min_len = int(min(roi_w, roi_h) * 0.10)
    edges = cv2.Canny(mask, 40, 120)
    lines = cv2.HoughLinesP(
        edges, rho=1, theta=np.pi / 180, threshold=35,
        minLineLength=max(min_len, 50), maxLineGap=18
    )
    if lines is None:
        return []
    out = []
    for line in lines:
        x1, y1, x2, y2 = map(int, line[0])
        length = np.hypot(x2 - x1, y2 - y1)
        if length < min_len:
            continue
        angle = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        angle_norm = angle % 180
        if angle_norm <= 25 or angle_norm >= 155:
            continue
        y1_full = y1 + roi_top
        y2_full = y2 + roi_top
        out.append((x1, y1_full, x2, y2_full))
    return out


def _filter_lines_by_vp(lines_px, vp, img_w, img_h, max_dist_ratio=0.25):
    """
    VP'den geçmeyen çizgileri at: Sadece kaçış noktasına yakın (perspektife uygun) çizgiler kalır.
    max_dist_ratio: çizgi-VP mesafesi bu oran * sqrt(w^2+h^2) altında olmalı.
    """
    import numpy as np
    if not vp or not lines_px:
        return lines_px
    vx, vy = vp
    diag = np.sqrt(img_w ** 2 + img_h ** 2)
    max_d = max(30, diag * max_dist_ratio)
    out = []
    for seg in lines_px:
        x1, y1, x2, y2 = seg
        d = _distance_point_to_line(vx, vy, x1, y1, x2, y2)
        if d <= max_d:
            out.append(seg)
    return out if out else lines_px


def _cluster_angles(angles, merge_thresh_rad=0.038):
    """
    Açıları kümele: birbirine merge_thresh_rad yakın olanlar tek radyal (ortalama açı).
    Daha sıkı eşik = yan yana iki sarı şerit ayrı sınır sayılır; slotlar boyalı çizgiden bölünür.
    """
    import numpy as np
    if not angles:
        return []
    angles = sorted(angles)
    clusters = [[angles[0]]]
    for a in angles[1:]:
        if a - clusters[-1][0] <= merge_thresh_rad:
            clusters[-1].append(a)
        else:
            clusters.append([a])
    return [float(np.mean(c)) for c in clusters]


def _radial_angles_from_lines_only(vp, lines_px):
    """
    Kural: yerde dikine K çizgi varsa -> K+1 bölme (sütun). Çizgiler bölmeleri böler; sıfırdan resme göre.
    Radyaller = sol sınır + çizgi1 açısı + ... + çizgiK açısı + sağ sınır -> (K+2) radyal = (K+1) sütun.
    """
    import numpy as np
    vx, vy = vp
    angles = []
    for (x1, y1, x2, y2) in lines_px:
        mx, my = (x1 + x2) / 2.0, (y1 + y2) / 2.0
        dx, dy = mx - vx, my - vy
        if dx * dx + dy * dy < 1:
            continue
        angles.append(np.arctan2(dy, dx))
    if not angles:
        return None, 0
    cluster_angles = _cluster_angles(angles)
    cluster_angles = sorted(cluster_angles)
    K = len(cluster_angles)
    if K > MAX_COLUMNS:
        cluster_angles = [cluster_angles[i] for i in np.linspace(0, K - 1, MAX_COLUMNS).astype(int)]
        K = len(cluster_angles)
    n_columns = K + 1
    if K == 1:
        a = cluster_angles[0]
        step = 0.15
        return [a - step, a, a + step], 2
    a0, aK = cluster_angles[0], cluster_angles[-1]
    step = (aK - a0)
    if step < 1e-6:
        step = 0.15
    radial_angles = [a0 - step] + cluster_angles + [aK + step]
    return radial_angles, n_columns


def _radial_angles_from_vp_and_lines(vp, lines_px, img_w, img_h, n_columns):
    """
    (Fallback) Sabit n_columns ile radyal açılar; çizgi kümelerinden veya aradan interpolasyon.
    USE_LINE_DRIVEN_COLUMNS=True iken _radial_angles_from_lines_only kullanılır.
    """
    import numpy as np
    vx, vy = vp
    n_radials = n_columns + 1
    lines_with_angle = []
    for (x1, y1, x2, y2) in lines_px:
        mx, my = (x1 + x2) / 2.0, (y1 + y2) / 2.0
        dx, dy = mx - vx, my - vy
        if dx * dx + dy * dy < 1:
            continue
        a = np.arctan2(dy, dx)
        lines_with_angle.append(a)
    if not lines_with_angle:
        angle_min, angle_max = -0.4, 0.4
        return [angle_min + (angle_max - angle_min) * i / max(1, n_radials - 1) for i in range(n_radials)]
    cluster_angles = _cluster_angles(lines_with_angle)
    cluster_angles = sorted(cluster_angles)
    if len(cluster_angles) >= n_radials:
        idx = np.linspace(0, len(cluster_angles) - 1, n_radials).astype(int)
        return [cluster_angles[i] for i in idx]
    a_min = cluster_angles[0]
    a_max = cluster_angles[-1]
    if a_max - a_min < 1e-6:
        a_max = a_min + 0.25
    return [a_min + (a_max - a_min) * k / max(1, n_radials - 1) for k in range(n_radials)]


def _horizontal_row_lines(img_w, img_h, n_rows, vp=None):
    """
    Dinamik ROI: VP verilirse satırlar VP'nin hemen altından başlar (ufuk çizgisi).
    ROI trapezoidinden n_rows+1 yatay satır sınırı (x1,y1,x2,y2) piksel.
    """
    bottom_y = int(ROI_BOTTOM_RATIO * img_h)
    if vp is not None:
        vx, vy = vp
        top_y = max(int(ROI_TOP_RATIO * img_h), int(vy) + 15)
    else:
        top_y = int(ROI_TOP_RATIO * img_h)
    left_top = int(0.12 * img_w)
    right_top = int(0.88 * img_w)
    left_bot = int(0.04 * img_w)
    right_bot = int(0.96 * img_w)
    lines = []
    for i in range(n_rows + 1):
        t = i / n_rows
        x1 = left_top + t * (left_bot - left_top)
        x2 = right_top + t * (right_bot - right_top)
        y1 = top_y + t * (bottom_y - top_y)
        y2 = y1
        lines.append((x1, y1, x2, y2))
    return lines


def _ray_line_intersection(vp, angle_rad, line_segment):
    """
    VP'den angle_rad yönünde çıkan ışın ile line_segment (x1,y1,x2,y2) doğrusunun kesişimi.
    Işın: P = vp + t*(cos(angle), sin(angle)), t > 0.
    """
    import numpy as np
    vx, vy = vp
    dx, dy = np.cos(angle_rad), np.sin(angle_rad)
    x1, y1, x2, y2 = line_segment
    den = dx * (y2 - y1) - dy * (x2 - x1)
    if abs(den) < 1e-8:
        return None
    t = ((x1 - vx) * (y2 - y1) - (y1 - vy) * (x2 - x1)) / den
    if t <= 0:
        return None
    px = vx + t * dx
    py = vy + t * dy
    return (float(px), float(py))


def _get_semantic_line_polylines(img_bgr, img_w, img_h):
    """
    Semantic fit: findContours (boyalı çizgiler) + approxPolyDP -> poligonlar;
    perspektif matrisiyle normalize edilmiş polyline listesi (her biri [(x,y), ...] 0-1).
    """
    import cv2
    import numpy as np
    src_quad = _get_src_quad_pixel(img_w, img_h)
    src_pts = np.array(src_quad, dtype=np.float32)
    dst_rect = np.array([
        [0, 0], [BIRDSEYE_W, 0], [BIRDSEYE_W, BIRDSEYE_H], [0, BIRDSEYE_H]
    ], dtype=np.float32)
    M = cv2.getPerspectiveTransform(src_pts, dst_rect)
    M_inv = cv2.getPerspectiveTransform(dst_rect, src_pts)
    birdseye = cv2.warpPerspective(img_bgr, M, (BIRDSEYE_W, BIRDSEYE_H))
    mask = _line_mask_hsv(birdseye)
    contours, _ = cv2.findContours(mask, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    segments = []
    min_area = (BIRDSEYE_W * BIRDSEYE_H) * 0.002
    for cnt in contours:
        if cv2.contourArea(cnt) < min_area:
            continue
        epsilon = 0.03 * cv2.arcLength(cnt, True)
        approx = cv2.approxPolyDP(cnt, epsilon, True)
        if len(approx) < 2:
            continue
        pts_be = approx.reshape(-1, 2).astype(np.float32).reshape(-1, 1, 2)
        pts_img = cv2.perspectiveTransform(pts_be, M_inv)
        seg = [(float(p[0][0]) / img_w, float(p[0][1]) / img_h) for p in pts_img]
        segments.append(seg)
    return segments


def _point_to_segment_dist(px, py, seg):
    """Nokta (px,py) normalize ile seg [(x1,y1),(x2,y2)] arası mesafe."""
    import numpy as np
    if len(seg) < 2:
        return float("inf"), (px, py)
    best_d, best_p = float("inf"), (px, py)
    for i in range(len(seg) - 1):
        x1, y1 = seg[i][0], seg[i][1]
        x2, y2 = seg[i + 1][0], seg[i + 1][1]
        dx, dy = x2 - x1, y2 - y1
        t = max(0, min(1, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy + 1e-10)))
        qx = x1 + t * dx
        qy = y1 + t * dy
        d = (px - qx) ** 2 + (py - qy) ** 2
        if d < best_d:
            best_d, best_p = d, (qx, qy)
    return best_d ** 0.5, best_p


def _snap_quads_to_semantic(quads, semantic_segments, max_dist_norm):
    """Izgara köşelerini yerdeki çizgi poligonlarına (semantic) yakın ise snap et."""
    if not semantic_segments or not quads:
        return quads
    out = []
    for quad in quads:
        new_corners = []
        for (cx, cy) in quad:
            best_d, best_p = float("inf"), (cx, cy)
            for seg in semantic_segments:
                if len(seg) < 2:
                    continue
                d, p = _point_to_segment_dist(cx, cy, seg)
                if d < best_d and d <= max_dist_norm:
                    best_d, best_p = d, p
            new_corners.append(best_p)
        out.append(new_corners)
    return out


def _build_structural_grid_from_vp(vp, radial_angles, row_lines, img_w, img_h):
    """
    Her slot: VP'den gelen iki ışınsal doğru (yan sınırlar) ile perspektife uygun iki yatay doğru
    (ön/arka sınırlar) arasında kalan 4 noktalı poligon. Radyal açılar gerçek çizgi kümelerinden
    veya VP tahmini; rastgele kutu yok.
    """
    import numpy as np
    if not vp or not radial_angles or not row_lines:
        return []
    n_cols = len(radial_angles) - 1
    n_rows = len(row_lines) - 1
    if n_cols < 1 or n_rows < 1:
        return []
    points = []
    for j, line in enumerate(row_lines):
        row_pts = []
        for i, angle in enumerate(radial_angles):
            p = _ray_line_intersection(vp, angle, line)
            if p is None:
                return []
            row_pts.append(p)
        points.append(row_pts)
    quads = []
    for r in range(n_rows):
        for c in range(n_cols):
            p00 = points[r][c]
            p10 = points[r][c + 1]
            p11 = points[r + 1][c + 1]
            p01 = points[r + 1][c]
            quad_norm = [
                (p00[0] / img_w, p00[1] / img_h),
                (p10[0] / img_w, p10[1] / img_h),
                (p11[0] / img_w, p11[1] / img_h),
                (p01[0] / img_w, p01[1] / img_h),
            ]
            quads.append(quad_norm)
    return quads


def _stabilize_quads(quads, key="default"):
    """
    Jitter azaltma: EMA (Exponential Moving Average). Önceki çerçeve köşeleri ile blend.
    Sadece aynı sayıda slot ve aynı key ile blend yapılır.
    """
    global _slot_stabilizer_cache
    if not quads:
        return quads
    prev = _slot_stabilizer_cache.get(key)
    alpha = STABILIZER_ALPHA
    if prev is None or len(prev) != len(quads):
        _slot_stabilizer_cache[key] = [list(q) for q in quads]
        return quads
    out = []
    for p_corners, n_corners in zip(prev, quads):
        if len(p_corners) != len(n_corners):
            out.append(list(n_corners))
            continue
        blended = [
            (alpha * n[0] + (1 - alpha) * p[0], alpha * n[1] + (1 - alpha) * p[1])
            for p, n in zip(p_corners, n_corners)
        ]
        out.append(blended)
    _slot_stabilizer_cache[key] = out
    return out


def _lines_from_birdseye_roi(img_bgr, img_w, img_h):
    """
    Tüm kareyi kuş bakışına çevirip dikine çizgileri ara; çizgileri orijinal piksel koordinatına döndür.
    _strong_lines_in_roi 0 döndüğünde denenir (kuş bakışında çizgiler daha belirgin olabilir).
    """
    import cv2
    import numpy as np
    src_quad = _get_src_quad_pixel(img_w, img_h)
    src_pts = np.array(src_quad, dtype=np.float32)
    dst_rect = np.array([[0, 0], [BIRDSEYE_W, 0], [BIRDSEYE_W, BIRDSEYE_H], [0, BIRDSEYE_H]], dtype=np.float32)
    M = cv2.getPerspectiveTransform(src_pts, dst_rect)
    M_inv = cv2.getPerspectiveTransform(dst_rect, src_pts)
    birdseye = cv2.warpPerspective(img_bgr, M, (BIRDSEYE_W, BIRDSEYE_H))
    mask = _line_mask_hsv(birdseye)
    min_len = int(min(BIRDSEYE_W, BIRDSEYE_H) * 0.08)
    lines = cv2.HoughLinesP(
        cv2.Canny(mask, 40, 120), rho=1, theta=np.pi / 180, threshold=30,
        minLineLength=max(min_len, 40), maxLineGap=20
    )
    if lines is None:
        return []
    out = []
    for line in lines:
        x1, y1, x2, y2 = map(int, line[0])
        angle = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        a = angle % 180
        if a <= 22 or a >= 158:
            continue
        pts = np.array([[[x1, y1]], [[x2, y2]]], dtype=np.float32)
        pts_img = cv2.perspectiveTransform(pts, M_inv)
        (px1, py1), (px2, py2) = pts_img[0, 0], pts_img[1, 0]
        out.append((int(px1), int(py1), int(px2), int(py2)))
    return out


def _lines_from_gray_roi(img_bgr, img_w, img_h):
    """
    Renk kullanmadan: ROI'de gri Canny + Hough ile dikine çizgiler.
    HSV sarı bulamasa bile zemin-çizgi kontrastı kenar verir.
    """
    import cv2
    import numpy as np
    h, w = img_bgr.shape[0], img_bgr.shape[1]
    roi_top = int(ROI_TOP_RATIO * h)
    roi_bottom = int(ROI_BOTTOM_RATIO * h)
    roi = img_bgr[roi_top:roi_bottom, :]
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 1.2)
    edges = cv2.Canny(blur, 25, 100)
    roi_h, roi_w = roi.shape[0], roi.shape[1]
    min_len = int(min(roi_w, roi_h) * 0.08)
    lines = cv2.HoughLinesP(
        edges, rho=1, theta=np.pi / 180, threshold=25,
        minLineLength=max(min_len, 35), maxLineGap=25
    )
    if lines is None:
        return []
    out = []
    for line in lines:
        x1, y1, x2, y2 = map(int, line[0])
        length = np.hypot(x2 - x1, y2 - y1)
        if length < min_len:
            continue
        angle = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        a = angle % 180
        if a <= 22 or a >= 158:
            continue
        out.append((x1, y1 + roi_top, x2, y2 + roi_top))
    return out


def _slots_from_vanishing_point_grid(img_bgr, img_w, img_h):
    """
    Kaçış noktası + yapısal ızgara: Güçlü çizgiler -> VP -> ışınsal + yatay çizgiler -> slot poligonları.
    Araç altında çizgi görünmese bile geometri VP'den çıkar. Başarısızsa None.
    """
    import numpy as np
    lines_px = _strong_lines_in_roi(img_bgr, img_w, img_h)
    if len(lines_px) < VP_MIN_LINES:
        lines_px = _lines_from_birdseye_roi(img_bgr, img_w, img_h)
    if len(lines_px) < VP_MIN_LINES:
        lines_px = _lines_from_gray_roi(img_bgr, img_w, img_h)
    if len(lines_px) < VP_MIN_LINES:
        logger.info("VP: yetersiz güçlü çizgi (%s)", len(lines_px))
        return None
    lines_abc = [_line_segment_to_abc(x1, y1, x2, y2) for (x1, y1, x2, y2) in lines_px]
    vp = _vanishing_point_least_squares(lines_abc)
    if vp is None:
        logger.info("VP: hesaplanamadı")
        return None
    vx, vy = vp
    h, w = img_bgr.shape[0], img_bgr.shape[1]
    if vy > h * 0.9 and vx < -w * 0.5 or vx > w * 1.5:
        logger.info("VP: mantıksız konum (vx=%.0f, vy=%.0f)", vx, vy)
        return None
    lines_px = _filter_lines_by_vp(lines_px, vp, w, h, max_dist_ratio=0.26)
    if len(lines_px) < 2:
        logger.info("VP: VP filtresi sonrası yetersiz çizgi (%s)", len(lines_px))
        return None
    n_rows = SLOT_GRID_ROWS
    if USE_LINE_DRIVEN_COLUMNS:
        radial_angles, n_cols = _radial_angles_from_lines_only(vp, lines_px)
        if radial_angles is None or n_cols < 1:
            radial_angles = _radial_angles_from_vp_and_lines(vp, lines_px, w, h, SLOT_GRID_COLS)
    else:
        n_cols = SLOT_GRID_COLS
        radial_angles = _radial_angles_from_vp_and_lines(vp, lines_px, w, h, n_cols)
    row_lines = _horizontal_row_lines(w, h, n_rows, vp=vp)
    quads = _build_structural_grid_from_vp(vp, radial_angles, row_lines, w, h)
    if not quads or len(quads) > VP_MAX_SLOTS:
        return None
    logger.info("VP grid: %s slot (kaçış noktası geometrisi)", len(quads))
    return quads


def _detect_parking_slots(image_bytes, camera_id=""):
    """
    Öncelik: 1) Admin kalibrasyonu (calibrations/AREA-xxx.json)
              2) Custom YOLO slot modeli (best.pt) ön tahmin
              3) VP/çizgi ızgara fallback
    """
    import slot_model

    area_id = (camera_id or "").strip()
    if area_id.startswith("AREA-"):
        calib = slot_model.load_calibration(area_id)
        if calib:
            quads_norm = slot_model.calibrated_quads_norm(calib)
            if quads_norm:
                occ_results = slot_model.check_occupancy_with_model(image_bytes, quads_norm)
                results = []
                for item in occ_results:
                    corners = item["corners"]
                    x, y, bw, bh = _quad_bbox(corners)
                    corners_pb = [detection_pb2.Point2D(x=float(c[0]), y=float(c[1])) for c in corners]
                    results.append(detection_pb2.ParkingSlotResult(
                        x=float(x), y=float(y), width=float(bw), height=float(bh),
                        occupied=bool(item["occupied"]),
                        confidence=float(item["confidence"]),
                        corners=corners_pb,
                    ))
                logger.info("DetectParkingSlots [kalibre %s]: %s slot", area_id, len(results))
                return results, quads_norm

    layout, img_w, img_h = slot_model.predict_slot_layout(image_bytes)
    if layout:
        quads = [p["corners"] for p in layout]
        results = []
        for pred in layout:
            corners = pred["corners"]
            x, y, bw, bh = _quad_bbox(corners)
            corners_pb = [detection_pb2.Point2D(x=float(c[0]), y=float(c[1])) for c in corners]
            results.append(detection_pb2.ParkingSlotResult(
                x=float(x), y=float(y), width=float(bw), height=float(bh),
                occupied=bool(pred.get("occupied", False)),
                confidence=float(pred.get("confidence", 0.0)),
                corners=corners_pb,
            ))
        logger.info("DetectParkingSlots [custom model]: %s slot", len(results))
        return results, quads

    if os.path.isfile(slot_model.get_model_path()):
        logger.info(
            "Custom slot model yüklü ama Bos/Dolu bulunamadı — VP fallback atlandı"
        )
        return [], []

    boxes, _, _ = _detect_cars_yolo(image_bytes)
    if _yolo_model is None:
        boxes, _, _ = _detect_vehicles_simulation(image_bytes)
    try:
        from PIL import Image
        import numpy as np
        import cv2
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        w, h = img.size
        img_bgr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    except Exception as e:
        logger.warning("Görüntü yüklenemedi: %s", e)
        w, h = 640, 480
        img_bgr = None
    quads = None
    if USE_VANISHING_POINT and img_bgr is not None:
        try:
            quads = _slots_from_vanishing_point_grid(img_bgr, w, h)
        except Exception as e:
            logger.warning("VP pipeline hatası: %s", e)
    if quads is None and USE_LINE_DETECTION and img_bgr is not None:
        try:
            quads = _slots_from_line_detection_pipeline(img_bgr, w, h)
            if quads is not None and len(quads) > VP_MAX_SLOTS:
                quads = None
        except Exception as e:
            logger.warning("Çizgi pipeline hatası: %s", e)
    if quads is None:
        quads = _perspective_grid_quads(w, h, FALLBACK_GRID_ROWS, FALLBACK_GRID_COLS)
        logger.info("Çizgi tespiti yok: fallback %sx%s = %s slot", FALLBACK_GRID_ROWS, FALLBACK_GRID_COLS, len(quads))
    if USE_SEMANTIC_SNAP and img_bgr is not None and quads:
        try:
            segments = _get_semantic_line_polylines(img_bgr, w, h)
            if segments:
                quads = _snap_quads_to_semantic(quads, segments, SEMANTIC_SNAP_MAX_DIST_NORM)
        except Exception as e:
            logger.debug("Semantic snap atlandı: %s", e)
    if USE_SLOT_STABILIZER and quads:
        quads = _stabilize_quads(quads, key=camera_id or "default")
    results = []
    for corners in quads:
        best_iou = 0.0
        best_conf = 0.0
        for b in boxes:
            iou = _iou_polygon_bbox(corners, b)
            if iou > best_iou:
                best_iou = iou
                best_conf = b.confidence
        occupied = best_iou >= IOU_OCCUPIED_THRESHOLD
        x, y, bw, bh = _quad_bbox(corners)
        corners_pb = [detection_pb2.Point2D(x=float(c[0]), y=float(c[1])) for c in corners]
        results.append(detection_pb2.ParkingSlotResult(
            x=float(x), y=float(y), width=float(bw), height=float(bh),
            occupied=occupied,
            confidence=best_conf,
            corners=corners_pb,
        ))
    logger.info("DetectParkingSlots: %s slot, %s dolu (IoU>%.2f)",
                len(results), sum(1 for r in results if r.occupied), IOU_OCCUPIED_THRESHOLD)
    return results, quads


# --- Perspektif düzeltme (kuş bakışı) taslak ---

def get_birdseye_transform(src_quad_pixel, out_width, out_height):
    """
    Kuş bakışı (Bird's Eye View) dönüşümü için kaynak dörtgen ve çıktı boyutu.
    src_quad_pixel: görüntüdeki 4 köşe (piksel) [(x,y), ...] sol-üst, sağ-üst, sağ-alt, sol-alt.
    Returns: 3x3 M (getPerspectiveTransform ile kullanılır).
    """
    try:
        import cv2
        import numpy as np
        src = np.array(src_quad_pixel, dtype=np.float32)
        dst = np.array([
            [0, 0],
            [out_width, 0],
            [out_width, out_height],
            [0, out_height],
        ], dtype=np.float32)
        M = cv2.getPerspectiveTransform(src, dst)
        return M
    except Exception as e:
        logger.warning("get_birdseye_transform: %s", e)
        return None


def warp_to_birdseye(image_np, src_quad_pixel, out_width, out_height):
    """
    Görüntüyü kuş bakışına dönüştür. Yerdeki çizgilerin ayrışması için kullanılabilir.
    image_np: BGR veya RGB; src_quad_pixel: 4 köşe (piksel).
    """
    try:
        import cv2
        import numpy as np
        M = get_birdseye_transform(src_quad_pixel, out_width, out_height)
        if M is None:
            return image_np
        return cv2.warpPerspective(image_np, M, (out_width, out_height))
    except Exception as e:
        logger.warning("warp_to_birdseye: %s", e)
        return image_np


# --- OpenCV görselleştirme (sadece ParkingSlot çerçeveleri) ---

def _draw_slots_opencv(image_bytes, slots_result, quads, img_w, img_h):
    """
    Sadece slot poligonları: Boş = (0,255,0) ince çerçeve. Dolu = (0,0,255) çerçeve + hafif şeffaf dolgu + OCCUPIED skor.
    Gürültü çizgisi yok; sadece ana çerçeve.
    """
    try:
        import cv2
        import numpy as np
    except ImportError as e:
        logger.warning("OpenCV yok, çizim atlanıyor: %s", e)
        return image_bytes
    try:
        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if img is None:
            from PIL import Image
            pil_img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
            img = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)
        h_img, w_img = img.shape[0], img.shape[1]
        draw_w = int(img_w)
        draw_h = int(img_h)
        if (w_img, h_img) != (draw_w, draw_h):
            img = cv2.resize(img, (draw_w, draw_h))
    except Exception as e:
        logger.warning("Görüntü decode hatası: %s", e)
        return image_bytes
    thin = max(1, min(draw_w, draw_h) // 400)
    font = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = max(0.35, min(draw_w, draw_h) / 1400.0)
    n_drawn = 0
    for slot, corners in zip(slots_result, quads):
        if not corners or len(corners) < 4:
            continue
        pts = np.array([
            [float(c[0]) * draw_w, float(c[1]) * draw_h]
            for c in corners[:4]
        ], dtype=np.int32)
        pts_contour = pts.reshape((-1, 1, 2))
        if slot.occupied:
            color_bgr = (0, 0, 255)
            overlay = img.copy()
            cv2.fillPoly(overlay, [pts], (0, 0, 255), lineType=cv2.LINE_AA)
            cv2.addWeighted(overlay, 0.2, img, 0.8, 0, img)
            cv2.polylines(img, [pts_contour], isClosed=True, color=color_bgr, thickness=thin + 1)
            label = "OCCUPIED %.0f%%" % (slot.confidence * 100)
            (tw, th), _ = cv2.getTextSize(label, font, font_scale, 1)
            cx = int((corners[0][0] + corners[2][0]) / 2 * draw_w) - tw // 2
            cy = int((corners[0][1] + corners[2][1]) / 2 * draw_h) + th // 2
            cv2.putText(img, label, (max(0, cx), min(draw_h - 2, cy)), font, font_scale, color_bgr, 1, cv2.LINE_AA)
        else:
            cv2.polylines(img, [pts_contour], isClosed=True, color=(0, 255, 0), thickness=thin)
        n_drawn += 1
    logger.info("Çizim: %s slot (statik poligon)", n_drawn)
    _, jpeg = cv2.imencode(".jpg", img)
    return jpeg.tobytes()


class YOLODetectionServicer(detection_pb2_grpc.YOLODetectionServiceServicer):
    """detection.proto YOLODetectionService implementasyonu."""

    def DetectVehicles(self, request, context):
        import cv_engine
        image_data = request.image_data if request.image_data else b""
        camera_id = request.camera_id or ""
        slot_id = request.slot_id or ""
        logger.info("DetectVehicles camera_id=%s slot_id=%s", camera_id, slot_id)
        vehicle_detected, confidence, boxes, _, _ = cv_engine.analyze_frame(
            image_data, camera_id=camera_id, slot_id=slot_id
        )
        return detection_pb2.DetectionResponse(
            vehicle_detected=vehicle_detected,
            confidence=confidence,
            bounding_boxes=boxes,
        )

    def DetectLicensePlate(self, request, context):
        import plate_reader
        import cv_engine
        image_data = request.image_data if request.image_data else b""
        camera_id = request.camera_id or ""
        slot_label = 0
        digits = "".join(c for c in camera_id if c.isdigit())
        if digits:
            try:
                slot_label = int(digits)
            except ValueError:
                slot_label = 0
        if len(image_data) < 100:
            return detection_pb2.LicensePlateResponse(
                license_plate_text="TESPIT EDILEMEDI",
                confidence=0.0,
            )
        bgr, _, _ = cv_engine._decode_image(image_data)
        result = plate_reader.process_and_read_detailed(bgr, slot_label)
        plate_text = result.get("text", "TESPIT EDILEMEDI")
        confidence = float(result.get("confidence", 0.0))
        loc = result.get("location")
        plate_loc = None
        if loc and len(loc) == 4:
            plate_loc = detection_pb2.BoundingBox(
                x=float(loc[0]), y=float(loc[1]),
                width=float(loc[2]), height=float(loc[3]),
                class_name="plate", confidence=confidence,
            )
        logger.info("DetectLicensePlate camera_id=%s -> %s (%.2f)", camera_id, plate_text, confidence)
        resp = detection_pb2.LicensePlateResponse(
            license_plate_text=plate_text,
            confidence=confidence,
        )
        if plate_loc is not None:
            resp.plate_location.CopyFrom(plate_loc)
        return resp

    def DetectParkingSlots(self, request, context):
        logger.info("DetectParkingSlots isteği alındı")
        image_data = request.image_data if request.image_data else b""
        camera_id = request.camera_id if request.camera_id else ""
        slots, _ = _detect_parking_slots(image_data, camera_id=camera_id)
        return detection_pb2.ParkingSlotsResponse(slots=slots)

    def DetectParkingSlotsImage(self, request, context):
        logger.info("DetectParkingSlotsImage isteği alındı")
        image_data = request.image_data if request.image_data else b""
        camera_id = request.camera_id if request.camera_id else ""
        slots, quads = _detect_parking_slots(image_data, camera_id=camera_id)
        try:
            from PIL import Image
            img = Image.open(io.BytesIO(image_data)).convert("RGB")
            img_w, img_h = img.size
        except Exception:
            img_w, img_h = 640, 480
        jpeg_bytes = _draw_slots_opencv(image_data, slots, quads, img_w, img_h)
        return detection_pb2.ParkSlotsImageResponse(image_jpeg=jpeg_bytes)

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
    executor = ThreadPoolExecutor(max_workers=4)
    server = grpc.server(executor)
    detection_pb2_grpc.add_YOLODetectionServiceServicer_to_server(
        YOLODetectionServicer(), server
    )
    listen_addr = f"127.0.0.1:{PORT}"
    server.add_insecure_port(listen_addr)
    server.start()
    logger.info("YOLO gRPC sunucusu dinleniyor: %s (poligon ROI, IoU>%.2f, OpenCV görsel)",
                listen_addr, IOU_OCCUPIED_THRESHOLD)
    def preload():
        _load_yolo()
        try:
            import slot_model
            slot_model._load_slot_model()
            logger.info("Parking slot model ön yüklendi: %s", slot_model.get_model_path())
        except Exception as e:
            logger.warning("Slot model ön yükleme başarısız: %s", e)
    t = threading.Thread(target=preload, daemon=True)
    t.start()
    try:
        import camera_simulator
        camera_simulator.start_camera_simulation()
        camera_simulator.start_http_server()
    except Exception as e:
        logger.warning("Kamera simülasyonu başlatılamadı: %s", e)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
