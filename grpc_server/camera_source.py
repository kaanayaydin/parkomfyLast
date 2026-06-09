"""
Unified camera frame source: simulated video loop or RTSP/IP camera.
Set PARKOMFY_RTSP_URL to use a real camera; otherwise falls back to video file.
"""
import logging
import os
import threading
import time
from abc import ABC, abstractmethod
from pathlib import Path

import cv2

logger = logging.getLogger(__name__)

_PROJECT_ROOT = Path(__file__).resolve().parent.parent


class CameraSource(ABC):
    @abstractmethod
    def start(self) -> None: ...

    @abstractmethod
    def stop(self) -> None: ...

    @abstractmethod
    def get_snapshot_jpeg(self) -> bytes: ...

    @abstractmethod
    def source_name(self) -> str: ...


class VideoFileSource(CameraSource):
    """Loop a local MP4 file (existing simulator behaviour)."""

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
            logger.error("Video not found: %s", self.video_path)
            return
        self._running = True
        self._thread = threading.Thread(target=self._play_loop, daemon=True, name="video-source")
        self._thread.start()
        logger.info("Video camera source started: %s", self.video_path)

    def stop(self):
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=3.0)
        self._thread = None

    def get_snapshot_jpeg(self) -> bytes:
        with self._lock:
            return self._latest_jpeg

    def source_name(self) -> str:
        return Path(self.video_path).name

    def _play_loop(self):
        while self._running:
            cap = cv2.VideoCapture(self.video_path)
            if not cap.isOpened():
                logger.error("Cannot open video: %s", self.video_path)
                time.sleep(2)
                continue
            fps = cap.get(cv2.CAP_PROP_FPS) or 24.0
            delay = 1.0 / max(fps, 1.0)
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


class RtspSource(CameraSource):
    """Read frames from RTSP/HTTP IP camera URL."""

    def __init__(self, rtsp_url: str, reconnect_sec: float = 3.0):
        self.rtsp_url = rtsp_url
        self.reconnect_sec = reconnect_sec
        self._lock = threading.Lock()
        self._latest_jpeg = None
        self._running = False
        self._thread = None

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._read_loop, daemon=True, name="rtsp-source")
        self._thread.start()
        logger.info("RTSP camera source started: %s", self.rtsp_url)

    def stop(self):
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=3.0)
        self._thread = None

    def get_snapshot_jpeg(self) -> bytes:
        with self._lock:
            return self._latest_jpeg

    def source_name(self) -> str:
        return self.rtsp_url

    def _read_loop(self):
        cap = None
        while self._running:
            if cap is None or not cap.isOpened():
                cap = cv2.VideoCapture(self.rtsp_url)
                if not cap.isOpened():
                    logger.warning("RTSP connect failed, retry in %.0fs", self.reconnect_sec)
                    time.sleep(self.reconnect_sec)
                    continue
                logger.info("RTSP connected")
            ret, frame = cap.read()
            if not ret:
                cap.release()
                cap = None
                time.sleep(self.reconnect_sec)
                continue
            ok, buf = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 85])
            if ok:
                with self._lock:
                    self._latest_jpeg = buf.tobytes()
            time.sleep(0.04)  # ~25 fps cap
        if cap is not None:
            cap.release()


def resolve_video_path(video_ref: str) -> Path:
    ref = (video_ref or "").strip()
    if not ref:
        return _PROJECT_ROOT / "loop1.mp4"
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


def create_camera_source(video_path=None) -> CameraSource:
    """
    Factory: RTSP if PARKOMFY_RTSP_URL set, else video file.
    Video override: video_path arg or PARKOMFY_CAMERA_VIDEO env.
    """
    rtsp = os.environ.get("PARKOMFY_RTSP_URL", "").strip()
    if rtsp:
        return RtspSource(rtsp)
    env = os.environ.get("PARKOMFY_CAMERA_VIDEO")
    path = resolve_video_path(video_path or env or "loop1")
    return VideoFileSource(str(path))
