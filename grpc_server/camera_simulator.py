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

# Entry/exit: plakaokuma.mp4 — loop1 video başı/sonu ile tetiklenir (ScheduledLoop1Simulator).
_GIRIS_VIDEO = "plakaokuma.mp4"
_CIKIS_VIDEO = "plakaokuma.mp4"
_SPECIAL_VIDEOS = {"giris": _GIRIS_VIDEO, "cikis": _CIKIS_VIDEO}
_LOOP1_FREEZE_SEC = float(os.environ.get("PARKOMFY_LOOP1_FREEZE_SEC", "30"))
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
        self._timeline_lock = threading.Lock()
        self._position_sec = 0.0
        self._duration_sec = 0.0
        self._loop_index = 0

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

    def get_timeline(self) -> dict:
        with self._timeline_lock:
            return {
                "positionSec": round(self._position_sec, 2),
                "durationSec": round(self._duration_sec, 2),
                "loopIndex": int(self._loop_index),
            }

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
            total_frames = cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0
            duration_sec = (total_frames / fps) if total_frames > 0 else 0.0
            with self._timeline_lock:
                self._duration_sec = duration_sec
            delay = 1.0 / fps
            frame_idx = 0
            while self._running:
                ret, frame = cap.read()
                if not ret:
                    break
                frame_idx += 1
                with self._timeline_lock:
                    self._position_sec = frame_idx / fps
                ok, buf = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 82])
                if ok:
                    with self._lock:
                        self._latest_jpeg = buf.tobytes()
                time.sleep(delay)
            cap.release()
            with self._timeline_lock:
                self._loop_index += 1
                self._position_sec = 0.0


class ScheduledLoop1Simulator(CameraSimulator):
    """
    loop1.mp4: başlangıçta giriş, bitişte çıkış plaka videosu;
    son kare FREEZE_SEC saniye dondurulur, sonra döngü tekrarlanır.
    """

    def __init__(self, lot_key: str, video_path: str, freeze_sec: float = None):
        super().__init__(lot_key, video_path)
        self._freeze_sec = freeze_sec if freeze_sec is not None else _LOOP1_FREEZE_SEC

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
            total_frames = cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0
            duration_sec = (total_frames / fps) if total_frames > 0 else 0.0
            delay = 1.0 / fps
            with self._timeline_lock:
                self._duration_sec = duration_sec
                self._position_sec = 0.0

            trigger_gate_camera("giris")
            logger.info(
                "[%s] Video başlangıcı: giriş plaka videosu (%.2fs döngü)",
                self.lot_key, duration_sec,
            )

            last_frame = None
            frame_idx = 0
            while self._running:
                ret, frame = cap.read()
                if not ret:
                    break
                last_frame = frame
                frame_idx += 1
                with self._timeline_lock:
                    self._position_sec = frame_idx / fps
                ok, buf = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 82])
                if ok:
                    with self._lock:
                        self._latest_jpeg = buf.tobytes()
                time.sleep(delay)
            cap.release()

            if not self._running:
                break

            trigger_gate_camera("cikis")
            logger.info(
                "[%s] Video sonu: çıkış plaka videosu, son kare %.0fs dondurulacak",
                self.lot_key, self._freeze_sec,
            )

            freeze_jpeg = None
            if last_frame is not None:
                ok, buf = cv2.imencode(".jpg", last_frame, [int(cv2.IMWRITE_JPEG_QUALITY), 82])
                if ok:
                    freeze_jpeg = buf.tobytes()

            with self._timeline_lock:
                self._position_sec = duration_sec

            freeze_end = time.time() + self._freeze_sec
            while self._running and time.time() < freeze_end:
                if freeze_jpeg:
                    with self._lock:
                        self._latest_jpeg = freeze_jpeg
                time.sleep(delay)

            with self._timeline_lock:
                self._loop_index += 1
                self._position_sec = 0.0


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


class TriggeredGateCameraSimulator(AnnotatedCameraSimulator):
    """
    plakaokuma.mp4: sürekli döngü yerine loop1 doluluk kenarında bir kez oynatılır.
    Boşta 'Bekleniyor' karesi gösterilir.
    """

    HOLD_AFTER_SEC = 0.35

    def __init__(self, lot_key: str, video_path: str, display_label: str):
        super().__init__(lot_key, video_path, ocr_interval=0.65)
        self._display_label = display_label
        self._trigger = threading.Event()
        self._playing = False
        self._idle_jpeg = self._build_idle_jpeg()

    def trigger(self):
        if self._playing:
            return
        self._trigger.set()

    def is_playing(self):
        return self._playing

    def _build_idle_jpeg(self):
        import numpy as np
        img = np.zeros((360, 640, 3), dtype=np.uint8)
        img[:] = (24, 26, 30)
        title = f"{self._display_label}"
        sub = "Bekleniyor..."
        cv2.putText(img, title, (36, 150), cv2.FONT_HERSHEY_SIMPLEX, 0.85, (200, 200, 200), 2)
        cv2.putText(img, sub, (36, 200), cv2.FONT_HERSHEY_SIMPLEX, 0.65, (120, 120, 130), 2)
        ok, buf = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), 72])
        return buf.tobytes() if ok else None

    def _play_loop(self):
        with self._lock:
            self._latest_jpeg = self._idle_jpeg
        while self._running:
            self._trigger.wait(timeout=0.2)
            if not self._running:
                break
            if not self._trigger.is_set():
                continue
            self._trigger.clear()
            self._playing = True
            try:
                self._play_once()
            finally:
                self._playing = False
                time.sleep(self.HOLD_AFTER_SEC)
                with self._lock:
                    self._latest_jpeg = self._idle_jpeg

    def _play_once(self):
        cap = cv2.VideoCapture(self.video_path)
        if not cap.isOpened():
            logger.error("[%s] Plaka videosu acilamadi: %s", self.lot_key, self.video_path)
            return
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


def trigger_gate_camera(gate: str) -> bool:
    """gate: giris | cikis"""
    key = _normalize_lot(gate)
    sim = _streams.get(key)
    if isinstance(sim, TriggeredGateCameraSimulator):
        sim.trigger()
        return True
    return False


def is_gate_playing(gate: str) -> bool:
    key = _normalize_lot(gate)
    sim = _streams.get(key)
    if isinstance(sim, TriggeredGateCameraSimulator):
        return sim.is_playing()
    return False


def get_gate_status() -> dict:
    return {
        "giris": {"playing": is_gate_playing("giris")},
        "cikis": {"playing": is_gate_playing("cikis")},
    }


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
        if lot == "loop1":
            sim = ScheduledLoop1Simulator(lot, str(path))
        else:
            sim = CameraSimulator(lot, str(path))
        sim.start()
        _streams[lot] = sim
    # Giriş/çıkış: plakaokuma.mp4 — loop1 video başı/sonu ile tetiklenir.
    gate_labels = {"giris": "Giris Kamerasi", "cikis": "Cikis Kamerasi"}
    for lot in ("giris", "cikis"):
        if lot in _streams:
            continue
        path = _resolve_video_path(lot)
        if not path.is_file():
            logger.warning("Giris/cikis videosu yok, atlaniyor: %s (%s)", lot, path)
            continue
        sim = TriggeredGateCameraSimulator(lot, str(path), gate_labels.get(lot, lot))
        sim.start()
        _streams[lot] = sim
    logger.info(
        "Paralel kamera stream'leri: %s (loop1: baslangic=giris, son=cikis, freeze=%.0fs)",
        list(_streams.keys()), _LOOP1_FREEZE_SEC,
    )


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
            sim = _streams.get(lot)
            tl = sim.get_timeline() if sim and hasattr(sim, "get_timeline") else {}
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
            self.send_header("Pragma", "no-cache")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Expose-Headers", "X-Parkomfy-Lot, X-Parkomfy-Position-Sec, X-Parkomfy-Loop-Index")
            self.send_header("X-Parkomfy-Lot", lot)
            self.send_header("X-Parkomfy-Position-Sec", str(tl.get("positionSec", 0)))
            self.send_header("X-Parkomfy-Loop-Index", str(tl.get("loopIndex", 0)))
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

        if path.startswith("/gate/status"):
            payload = get_gate_status()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(payload).encode("utf-8"))
            return

        if path.startswith("/timeline"):
            m = re.match(r"/timeline/([\w]+)", path)
            lot = _normalize_lot(m.group(1) if m else "loop1")
            sim = _streams.get(lot)
            tl = sim.get_timeline() if sim and hasattr(sim, "get_timeline") else {}
            payload = {"lot": lot, **tl}
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(payload).encode("utf-8"))
            return

        if path.startswith("/streams"):
            payload = {
                "streams": [
                    {
                        "lot": lot,
                        "video": f"{lot}.mp4",
                        "hasFrame": bool(sim and sim.get_snapshot()),
                        "preview": lot == _preview_lot,
                        "timeline": sim.get_timeline()
                        if sim and hasattr(sim, "get_timeline")
                        else {},
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
        if self.path.startswith("/gate/trigger"):
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length) if length else b"{}"
            try:
                data = json.loads(body.decode("utf-8") or "{}")
            except json.JSONDecodeError:
                data = {}
            gate = data.get("gate") or data.get("camera") or ""
            ok = trigger_gate_camera(gate)
            status = 200 if ok else 400
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(
                json.dumps({"success": ok, "gate": _normalize_lot(gate)}).encode("utf-8")
            )
            return
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

