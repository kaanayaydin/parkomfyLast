"""
3 otopark videosu (loop1/2/3.mp4) eşzamanlı döngü — her lot_key ayrı stream.
HTTP: 127.0.0.1:50052
  GET  /snapshot/{lot}.jpg   — lot: loop1, loop2, loop3
  GET  /snapshot.jpg?lot=loop1
  GET  /streams              — tüm kameraların durumu
  POST /switch               — admin önizleme (tek stream değil, preview lot)
"""
import json
import logging
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import cv2

logger = logging.getLogger(__name__)

HTTP_PORT = int(os.environ.get("PARKOMFY_CAMERA_HTTP_PORT", "50052"))
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_DEFAULT_LOTS = ("loop1", "loop2", "loop3")

# Entry/exit cameras: dedicated mock videos, served with live plate annotation.
# Exit currently reuses the entry video (no dedicated exit clip yet); swap
# _CIKIS_VIDEO to a real exit clip when available.
_GIRIS_VIDEO = "otopark_giris_video.mp4"
_CIKIS_VIDEO = "otopark_giris_video.mp4"
_SPECIAL_VIDEOS = {"giris": _GIRIS_VIDEO, "cikis": _CIKIS_VIDEO}

_streams = {}
_preview_lot = "loop1"
_lock = threading.Lock()
# Serializes OCR calls across annotated streams (PaddleOCR shared reader).
_ocr_lock = threading.Lock()


class CameraSimulator:
    def __init__(self, lot_key: str, video_path: str):
        self.lot_key = lot_key
        self.video_path = video_path
        self._lock = threading.Lock()
        self._latest_jpeg = None
        self._running = False
        self._thread = None

    def start(self):
        if self._running:
            return
        if not os.path.isfile(self.video_path):
            logger.error("[%s] Video bulunamadı: %s", self.lot_key, self.video_path)
            return
        self._running = True
        self._thread = threading.Thread(
            target=self._play_loop, daemon=True, name=f"camera-{self.lot_key}"
        )
        self._thread.start()
        logger.info("[%s] Kamera simülasyonu başladı: %s", self.lot_key, self.video_path)

    def stop(self):
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=3.0)
        self._thread = None

    def get_snapshot(self) -> bytes:
        with self._lock:
            return self._latest_jpeg

    def _play_loop(self):
        while self._running:
            cap = cv2.VideoCapture(self.video_path)
            if not cap.isOpened():
                logger.error("[%s] Video açılamadı: %s", self.lot_key, self.video_path)
                time.sleep(2)
                continue
            fps = cap.get(cv2.CAP_PROP_FPS)
            if not fps or fps < 1:
                fps = 24.0
            delay = 1.0 / fps
            while self._running:
                ret, frame = cap.read()
                if not ret:
                    break
                ok, buf = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 82])
                if ok:
                    with self._lock:
                        self._latest_jpeg = buf.tobytes()
                time.sleep(delay)
            cap.release()


def _format_plate(text: str) -> str:
    """'34EZS794' -> '34 EZS 794' if it matches TR format, else as-is."""
    try:
        import plate_reader
        m = plate_reader.TR_PLATE_RE.match(text or "")
        if m:
            return f"{m.group(1)} {m.group(2)} {m.group(3)}"
    except Exception:
        pass
    return text or ""


class AnnotatedCameraSimulator(CameraSimulator):
    """
    Plays a video and overlays live plate detection (green box + plate text +
    confidence). OCR is throttled in a background thread so playback stays smooth;
    the latest detection is cached and drawn onto every served frame for ~2s.
    """

    # Serve a downscaled frame so phones can fetch it fast (full-res snapshots
    # are ~390KB and stall polling -> black screen). OCR still uses raw frame.
    # 640px: zayif Android'de siyah/takilma azaltmak icin loop1 boyutuna yakin.
    _SERVE_WIDTH = 640

    # OCR is CPU-heavy; run it sparingly so it doesn't starve frame serving
    # (Python GIL). Detection stays on screen via _DET_TTL between runs.
    _OCR_INPUT_WIDTH = 900
    _DET_TTL = 3.5

    def __init__(self, lot_key: str, video_path: str, ocr_interval: float = 1.6):
        super().__init__(lot_key, video_path)
        self._ocr_interval = ocr_interval
        self._raw_frame = None
        self._raw_lock = threading.Lock()
        self._det = None  # {"box": (x,y,w,h) norm, "text": str, "conf": float, "ts": float}

    def start(self):
        if self._running:
            return
        super().start()
        if self._running:
            threading.Thread(
                target=self._ocr_worker, daemon=True, name=f"ocr-{self.lot_key}"
            ).start()

    def _ocr_worker(self):
        try:
            import plate_reader
        except Exception as e:
            logger.warning("[%s] OCR devre disi (plate_reader yok): %s", self.lot_key, e)
            return
        while self._running:
            time.sleep(self._ocr_interval)
            with self._raw_lock:
                frame = None if self._raw_frame is None else self._raw_frame.copy()
            if frame is None:
                continue
            # Downscale before OCR to cut per-call CPU time (~2x faster).
            fw = frame.shape[1]
            if self._OCR_INPUT_WIDTH > 0 and fw > self._OCR_INPUT_WIDTH:
                s = self._OCR_INPUT_WIDTH / float(fw)
                frame = cv2.resize(frame, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
            try:
                with _ocr_lock:
                    res = plate_reader.process_and_read_detailed(frame, slot_id=self.lot_key)
            except Exception as e:
                logger.debug("[%s] OCR hata: %s", self.lot_key, e)
                continue
            text = res.get("text")
            loc = res.get("location")
            if text and text != plate_reader.FAIL_TEXT and loc:
                self._det = {
                    "box": loc,
                    "text": _format_plate(text),
                    "conf": float(res.get("confidence", 0.0)),
                    "ts": time.time(),
                }

    def _annotate(self, frame):
        det = self._det
        if not det or (time.time() - det["ts"]) > self._DET_TTL:
            return frame
        h, w = frame.shape[:2]
        x, y, bw, bh = det["box"]
        x1, y1 = int(x * w), int(y * h)
        x2, y2 = int((x + bw) * w), int((y + bh) * h)
        pad = max(4, int((y2 - y1) * 0.4))
        x1, y1, x2, y2 = x1 - pad, y1 - pad, x2 + pad, y2 + pad
        disp = frame.copy()
        cv2.rectangle(disp, (x1, y1), (x2, y2), (0, 230, 0), 3)
        label = f"{det['text']}  %{det['conf'] * 100:.0f}"
        scale = max(0.6, min(1.2, w / 1280.0))
        thick = 2 if scale < 0.9 else 3
        (tw, th), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, scale, thick)
        ly = max(th + 10, y1 - 6)
        cv2.rectangle(disp, (x1, ly - th - 10), (x1 + tw + 12, ly), (0, 230, 0), -1)
        cv2.putText(disp, label, (x1 + 6, ly - 6),
                    cv2.FONT_HERSHEY_SIMPLEX, scale, (0, 0, 0), thick)
        return disp

    def _play_loop(self):
        while self._running:
            cap = cv2.VideoCapture(self.video_path)
            if not cap.isOpened():
                logger.error("[%s] Video acilamadi: %s", self.lot_key, self.video_path)
                time.sleep(2)
                continue
            fps = cap.get(cv2.CAP_PROP_FPS)
            if not fps or fps < 1:
                fps = 24.0
            delay = 1.0 / fps
            while self._running:
                ret, frame = cap.read()
                if not ret:
                    break
                with self._raw_lock:
                    self._raw_frame = frame
                disp = self._annotate(frame)
                dh, dw = disp.shape[:2]
                if self._SERVE_WIDTH > 0 and dw > self._SERVE_WIDTH:
                    s = self._SERVE_WIDTH / float(dw)
                    disp = cv2.resize(disp, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
                ok, buf = cv2.imencode(".jpg", disp, [int(cv2.IMWRITE_JPEG_QUALITY), 72])
                if ok:
                    with self._lock:
                        self._latest_jpeg = buf.tobytes()
                time.sleep(delay)
            cap.release()


def _normalize_lot(lot_ref: str) -> str:
    ref = (lot_ref or "").strip().lower().replace(".mp4", "")
    if ref.startswith("loop"):
        return ref
    if ref in ("giris", "entry", "giriş"):
        return "giris"
    if ref in ("cikis", "exit", "çıkış", "cikis_v"):
        return "cikis"
    if ref in ("1", "2", "3"):
        return f"loop{ref}"
    if ref.startswith("istasyon"):
        n = ref.replace("istasyon", "")
        if n.isdigit():
            return f"loop{n}"
    return ref or "loop1"


def _resolve_video_path(lot_key: str) -> Path:
    key = _normalize_lot(lot_key)
    if key in _SPECIAL_VIDEOS:
        sp = _PROJECT_ROOT / _SPECIAL_VIDEOS[key]
        if sp.is_file():
            return sp
    p = _PROJECT_ROOT / f"{key}.mp4"
    if p.is_file():
        return p
    return _PROJECT_ROOT / "loop1.mp4"


def list_available_videos():
    out = []
    for i in range(1, 10):
        name = f"loop{i}.mp4"
        key = f"loop{i}"
        path = _PROJECT_ROOT / name
        if path.is_file():
            out.append({"id": key, "name": name, "label": f"Otopark {i}", "lot_key": key})
    return out


def get_stream(lot_key: str) -> CameraSimulator:
    key = _normalize_lot(lot_key)
    return _streams.get(key)


def get_snapshot_for_lot(lot_key: str) -> bytes:
    sim = get_stream(lot_key)
    return sim.get_snapshot() if sim else None


def get_current_video_name() -> str:
    with _lock:
        return f"{_preview_lot}.mp4"


def set_preview_lot(lot_key: str):
    global _preview_lot
    with _lock:
        _preview_lot = _normalize_lot(lot_key)


def start_all_cameras():
    """loop1, loop2, loop3 — hepsini paralel başlat."""
    global _streams
    for lot in _DEFAULT_LOTS:
        path = _resolve_video_path(lot)
        if not path.is_file():
            logger.warning("Video yok, atlanıyor: %s", path)
            continue
        if lot in _streams:
            continue
        sim = CameraSimulator(lot, str(path))
        sim.start()
        _streams[lot] = sim
    # Annotated entry/exit cameras (plate box + text + confidence overlay).
    for lot in ("giris", "cikis"):
        if lot in _streams:
            continue
        path = _resolve_video_path(lot)
        if not path.is_file() or path.name not in _SPECIAL_VIDEOS.values():
            logger.warning("Giris/cikis videosu yok, atlaniyor: %s", lot)
            continue
        sim = AnnotatedCameraSimulator(lot, str(path))
        sim.start()
        _streams[lot] = sim
    logger.info("Paralel kamera stream'leri: %s", list(_streams.keys()))


def start_camera_simulation(video_path=None):
    """Geriye uyumluluk: tüm lotları başlat."""
    start_all_cameras()
    if video_path:
        set_preview_lot(video_path)
    return get_stream(_preview_lot)


def switch_camera_video(video_ref: str):
    """Admin önizleme lot'u (stream'leri durdurmaz)."""
    key = _normalize_lot(video_ref)
    if key not in _streams:
        path = _resolve_video_path(key)
        if not path.is_file():
            return False, f"Video bulunamadı: {video_ref}"
        sim = CameraSimulator(key, str(path))
        sim.start()
        _streams[key] = sim
    set_preview_lot(key)
    return True, f"{key}.mp4"


def get_simulator() -> CameraSimulator:
    return get_stream(_preview_lot)


class _Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        logger.debug("camera-http: " + fmt, *args)

    def _lot_from_path(self):
        path = self.path.split("?")[0]
        m = re.match(r"/snapshot/([\w]+)(?:\.jpg)?", path)
        if m:
            return _normalize_lot(m.group(1))
        qs = parse_qs(urlparse(self.path).query)
        if "lot" in qs:
            return _normalize_lot(qs["lot"][0])
        return _preview_lot

    def do_GET(self):
        path = self.path.split("?")[0]

        if path.startswith("/snapshot"):
            lot = self._lot_from_path()
            jpeg = get_snapshot_for_lot(lot)
            if not jpeg:
                self.send_response(503)
                self.end_headers()
                self.wfile.write(f"no frame for {lot}".encode())
                return
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("X-Parkomfy-Lot", lot)
            self.end_headers()
            self.wfile.write(jpeg)
            return

        if path.startswith("/live.mjpg") or path.startswith("/mjpeg"):
            lot = self._lot_from_path()
            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            try:
                while True:
                    jpeg = get_snapshot_for_lot(lot)
                    if jpeg:
                        self.wfile.write(b"--frame\r\n")
                        self.wfile.write(b"Content-Type: image/jpeg\r\n\r\n")
                        self.wfile.write(jpeg)
                        self.wfile.write(b"\r\n")
                        self.wfile.flush()
                    time.sleep(0.08)
            except (BrokenPipeError, ConnectionResetError):
                pass
            return

        if path.startswith("/streams"):
            payload = {
                "streams": [
                    {
                        "lot": lot,
                        "video": f"{lot}.mp4",
                        "hasFrame": bool(sim and sim.get_snapshot()),
                        "preview": lot == _preview_lot,
                    }
                    for lot, sim in _streams.items()
                ],
                "preview": _preview_lot,
            }
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(payload).encode("utf-8"))
            return

        if path.startswith("/health") or path.startswith("/status"):
            payload = {
                "status": "UP",
                "preview": _preview_lot,
                "video": get_current_video_name(),
                "streams": list(_streams.keys()),
            }
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(payload).encode("utf-8"))
            return

        if path.startswith("/videos"):
            vids = list_available_videos()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                json.dumps({"videos": vids, "current": get_current_video_name()}).encode("utf-8")
            )
            return

        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        if self.path.startswith("/switch"):
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length) if length else b"{}"
            try:
                data = json.loads(body.decode("utf-8") or "{}")
            except json.JSONDecodeError:
                data = {}
            video = data.get("video") or data.get("name") or data.get("lot") or ""
            ok, msg = switch_camera_video(video)
            status = 200 if ok else 400
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                json.dumps({"success": ok, "message": msg, "current": get_current_video_name()}).encode("utf-8")
            )
            return
        self.send_response(404)
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()


def start_http_server():
    def run():
        # ThreadingHTTPServer: multiple cameras polled at once (otopark + giris +
        # cikis) must be served in parallel; a single-threaded server serializes
        # them and starves the extra streams -> black screens on the phone.
        server = ThreadingHTTPServer(("127.0.0.1", HTTP_PORT), _Handler)
        server.daemon_threads = True
        logger.info(
            "Kamera HTTP (paralel, threaded): http://127.0.0.1:%s/snapshot/loop1.jpg ... loop3.jpg",
            HTTP_PORT,
        )
        server.serve_forever()

    t = threading.Thread(target=run, daemon=True, name="camera-http")
    t.start()

