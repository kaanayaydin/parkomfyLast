#!/usr/bin/env python3
"""
PARKOMFY performans olcumu: latency + FPS (otomatik, etiket gerekmez).

Olculenler:
  - Plaka OCR (plate_reader.process_and_read_detailed)
  - Slot doluluk (hybrid_occupancy.analyze_calibrated_area, AREA-004)

Her biri icin N kare uzerinde calistirip ms cinsinden
mean/median/p95/min/max ve FPS (1000/mean) raporlar.

Kullanim:
  python benchmark.py
  python benchmark.py --ocr-video ../giris.mp4 --slot-video ../yen1.mp4 -n 30
  python benchmark.py --area AREA-004 --warmup 2
"""
import argparse
import statistics
import sys
import time
from pathlib import Path

import cv2

import plate_reader

_ROOT = Path(__file__).resolve().parent.parent


def _sample_frames(video_path: Path, n: int):
    """Videoyu esit araliklarla orneklenmis n kare olarak dondur."""
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        return []
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) or 0
    frames = []
    if total > 0:
        step = max(1, total // n)
        idx = 0
        while len(frames) < n:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ok, fr = cap.read()
            if not ok:
                break
            frames.append(fr)
            idx += step
    else:
        while len(frames) < n:
            ok, fr = cap.read()
            if not ok:
                break
            frames.append(fr)
    cap.release()
    return frames


def _report(name: str, times_ms: list):
    if not times_ms:
        print(f"\n[{name}] olcum yok (kare/video bulunamadi).")
        return
    times_ms.sort()
    mean = statistics.mean(times_ms)
    median = statistics.median(times_ms)
    p95 = times_ms[min(len(times_ms) - 1, int(len(times_ms) * 0.95))]
    print(f"\n=== {name} ({len(times_ms)} kare) ===")
    print(f"  mean   : {mean:7.1f} ms   ({1000.0 / mean:5.1f} FPS)")
    print(f"  median : {median:7.1f} ms   ({1000.0 / median:5.1f} FPS)")
    print(f"  p95    : {p95:7.1f} ms")
    print(f"  min/max: {times_ms[0]:7.1f} / {times_ms[-1]:7.1f} ms")


def bench_ocr(video_path: Path, n: int, warmup: int):
    frames = _sample_frames(video_path, n)
    if not frames:
        print(f"OCR: video okunamadi: {video_path}", file=sys.stderr)
        return
    for fr in frames[:warmup]:
        plate_reader.process_and_read_detailed(fr)
    times, hits = [], 0
    for fr in frames:
        t0 = time.perf_counter()
        det = plate_reader.process_and_read_detailed(fr)
        times.append((time.perf_counter() - t0) * 1000.0)
        if det.get("text") and det["text"] != plate_reader.FAIL_TEXT:
            hits += 1
    _report("Plaka OCR", times)
    print(f"  plaka okunan kare: {hits}/{len(frames)}")


def bench_slots(video_path: Path, area_id: str, n: int, warmup: int):
    try:
        import hybrid_occupancy
    except Exception as e:
        print(f"Slot olcumu atlandi (import): {e}", file=sys.stderr)
        return
    frames = _sample_frames(video_path, n)
    if not frames:
        print(f"Slot: video okunamadi: {video_path}", file=sys.stderr)
        return
    jpegs = []
    for fr in frames:
        ok, buf = cv2.imencode(".jpg", fr)
        if ok:
            jpegs.append(buf.tobytes())
    for jb in jpegs[:warmup]:
        hybrid_occupancy.analyze_calibrated_area(jb, area_id)
    times = []
    last = []
    for jb in jpegs:
        t0 = time.perf_counter()
        res = hybrid_occupancy.analyze_calibrated_area(jb, area_id)
        times.append((time.perf_counter() - t0) * 1000.0)
        last = res
    _report(f"Slot doluluk ({area_id})", times)
    if last:
        dolu = sum(1 for r in last if r["occupied"])
        print(f"  son kare: {len(last)} slot, {dolu} dolu")
    else:
        print(f"  UYARI: {area_id} kalibrasyonu bulunamadi (calibrations/{area_id}.json).")


def main():
    ap = argparse.ArgumentParser(description="PARKOMFY latency/FPS benchmark")
    ap.add_argument("--ocr-video", default=str(_ROOT / "otopark_giris_video.mp4"))
    ap.add_argument("--slot-video", default=str(_ROOT / "yen1.mp4"))
    ap.add_argument("--area", default="AREA-004")
    ap.add_argument("-n", "--num", type=int, default=30, help="olculecek kare sayisi")
    ap.add_argument("--warmup", type=int, default=2, help="model isinma kare sayisi (raporlanmaz)")
    args = ap.parse_args()

    print(f"GPU (EasyOCR): {plate_reader._gpu_available()}")
    bench_ocr(Path(args.ocr_video), args.num, args.warmup)
    bench_slots(Path(args.slot_video), args.area, args.num, args.warmup)
    print("\nNot: ilk cagri model yuklemesi icin yavas olur; warmup bunu dislar.")


if __name__ == "__main__":
    main()
