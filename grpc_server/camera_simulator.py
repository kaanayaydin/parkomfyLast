"""
loop1/2/3.mp4 dosyasını uç uca döngüde oynatarak canlı kamera simülasyonu.
HTTP: 127.0.0.1:50052  /snapshot.jpg  /live.mjpg  /videos  /switch
"""
import json
import logging
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import cv2

logger = logging.getLogger(__name__)

HTTP_PORT = int(os.environ.get("PARKOMFY_CAMERA_HTTP_PORT", "50052"))
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_DEFAULT_VIDEO = _PROJECT_ROOT / "loop1.mp4"

_simulator = None
_current_video_name = "loop1.mp4"


class CameraSimulator:
    def __init__(self, video_path: str):
        self.video_path = video_path
        self._lock = threading.Lock()
        self._latest_jpeg = None
        self._running = False
        self._thread = None

    def start(self):
        if self._running:
            return
        if not os.path.isfile(self.video_path):
            logger.error("Kamera video bulunamadı: %s", self.video_path)
            return
        self._running = True
        self._thread = threading.Thread(target=self._play_loop, daemon=True, name="camera-sim")
        self._thread.start()
        logger.info("Canlı kamera simülasyonu başladı: %s", self.video_path)

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
                logger.error("Video açılamadı: %s", self.video_path)
                time.sleep(2)
                continue
            fps = cap.get(cv2.CAP_PROP_FPS)
            if not fps or fps < 1:
                fps = 24.0
            delay = 1.0 / fps
            logger.info("Video döngüsü: %.1f fps", fps)
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
            if self._running:
                logger.debug("Video sonu — başa sarılıyor")


class _Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        logger.debug("camera-http: " + fmt, *args)

    def do_GET(self):
        sim = get_simulator()
        if self.path.startswith("/snapshot"):
            jpeg = sim.get_snapshot() if sim else None
            if not jpeg:
                self.send_response(503)
                self.end_headers()
                self.wfile.write(b"no frame")
                return
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(jpeg)
            return

        if self.path.startswith("/live.mjpg") or self.path.startswith("/mjpeg"):
            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            try:
                while True:
                    jpeg = sim.get_snapshot() if sim else None
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

        if self.path.startswith("/health") or self.path.startswith("/status"):
            sim = get_simulator()
            payload = {
                "status": "UP",
                "video": get_current_video_name(),
                "path": sim.video_path if sim else "",
                "hasFrame": bool(sim and sim.get_snapshot()),
            }
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(payload).encode("utf-8"))
            return

        if self.path.startswith("/videos"):
            vids = list_available_videos()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"videos": vids, "current": get_current_video_name()}).encode("utf-8"))
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
            video = data.get("video") or data.get("name") or ""
            ok, msg = switch_camera_video(video)
            status = 200 if ok else 400
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"success": ok, "message": msg, "current": get_current_video_name()}).encode("utf-8"))
            return
        self.send_response(404)
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()


def get_simulator() -> CameraSimulator:
    return _simulator


def get_current_video_name() -> str:
    return _current_video_name


def list_available_videos():
    """loop1.mp4, loop2.mp4, loop3.mp4"""
    out = []
    for i in range(1, 10):
        name = f"loop{i}.mp4"
        path = _PROJECT_ROOT / name
        if path.is_file():
            out.append({"id": f"loop{i}", "name": name, "label": f"Loop {i}"})
    return out


def _resolve_video_path(video_ref: str) -> Path:
    ref = (video_ref or "").strip()
    if not ref:
        return _DEFAULT_VIDEO
    if ref.endswith(".mp4"):
        p = _PROJECT_ROOT / ref
        if p.is_file():
            return p
    if not ref.endswith(".mp4"):
        p = _PROJECT_ROOT / f"{ref}.mp4"
        if p.is_file():
            return p
    p = Path(ref)
    if p.is_file():
        return p
    return _PROJECT_ROOT / "loop1.mp4"


def switch_camera_video(video_ref: str):
    global _simulator, _current_video_name
    path = _resolve_video_path(video_ref)
    if not path.is_file():
        return False, f"Video bulunamadı: {video_ref}"
    if _simulator is not None:
        _simulator.stop()
    _simulator = CameraSimulator(str(path))
    _simulator.start()
    _current_video_name = path.name
    logger.info("Kamera videosu değiştirildi: %s", _current_video_name)
    return True, _current_video_name


def start_camera_simulation(video_path=None):
    global _simulator, _current_video_name
    if _simulator is not None:
        return _simulator
    env = os.environ.get("PARKOMFY_CAMERA_VIDEO")
    path = _resolve_video_path(video_path or env or "loop1")
    _current_video_name = path.name
    _simulator = CameraSimulator(str(path))
    _simulator.start()
    return _simulator


def start_http_server():
    def run():
        server = HTTPServer(("127.0.0.1", HTTP_PORT), _Handler)
        logger.info("Kamera HTTP simülasyonu: http://127.0.0.1:%s/snapshot.jpg", HTTP_PORT)
        server.serve_forever()

    t = threading.Thread(target=run, daemon=True, name="camera-http")
    t.start()
