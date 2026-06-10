#!/usr/bin/env python3
"""
Slot doluluk dogrulugu: precision / recall / F1 / accuracy + confusion matrix.

Iki adimli kullanim (manuel sayim karsilastirmasi - rapordaki yontem):

1) TAHMIN URET (videodan kare ornekle, modeli calistir, CSV yaz):
     python eval_slots.py predict --video ../loop1.mp4 --area AREA-004 \
            --n 20 --out slots_pred.csv
   Cikan CSV sutunlari: frame,slot_number,pred_occupied,truth_occupied
   'truth_occupied' BOS gelir. Her satira videodaki o karede slot gercekten
   dolu mu (1) bos mu (0) elle yaz. Kareler --save-frames ile diske de yazilir.

2) SKORLA (truth doldurulmus CSV'den metrik hesapla):
     python eval_slots.py score --csv slots_pred.csv

Pozitif sinif = DOLU (occupied=1).
"""
import argparse
import csv
import sys
from pathlib import Path

import cv2

_ROOT = Path(__file__).resolve().parent.parent


def _sample_indices(total: int, n: int):
    if total <= 0:
        return list(range(n))
    step = max(1, total // n)
    return [i * step for i in range(n) if i * step < total]


def cmd_predict(args):
    import hybrid_occupancy

    video = Path(args.video)
    cap = cv2.VideoCapture(str(video))
    if not cap.isOpened():
        print(f"Video acilamadi: {video}", file=sys.stderr)
        return 1
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) or 0
    indices = _sample_indices(total, args.n)

    frames_dir = None
    if args.save_frames:
        frames_dir = Path(args.save_frames)
        frames_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for fi in indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, fi)
        ok, fr = cap.read()
        if not ok:
            continue
        ok2, buf = cv2.imencode(".jpg", fr)
        if not ok2:
            continue
        res = hybrid_occupancy.analyze_calibrated_area(buf.tobytes(), args.area)
        if not res:
            print(f"UYARI: {args.area} kalibrasyonu yok ya da slot dondurmedi.", file=sys.stderr)
        if frames_dir is not None:
            cv2.imwrite(str(frames_dir / f"frame_{fi:06d}.jpg"), fr)
        for r in res:
            rows.append({
                "frame": fi,
                "slot_number": r["slot_number"],
                "pred_occupied": 1 if r["occupied"] else 0,
                "truth_occupied": "",
            })
    cap.release()

    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["frame", "slot_number", "pred_occupied", "truth_occupied"])
        w.writeheader()
        w.writerows(rows)
    print(f"{len(rows)} satir yazildi: {args.out}")
    print("Simdi 'truth_occupied' sutununu elle doldur (1=dolu, 0=bos), sonra:")
    print(f"  python eval_slots.py score --csv {args.out}")
    if frames_dir:
        print(f"Kareler: {frames_dir}/ (etiketlerken bakabilirsin)")
    return 0


def cmd_score(args):
    tp = tn = fp = fn = 0
    skipped = 0
    with open(args.csv, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            t = (row.get("truth_occupied") or "").strip()
            p = (row.get("pred_occupied") or "").strip()
            if t not in ("0", "1") or p not in ("0", "1"):
                skipped += 1
                continue
            pred, truth = int(p), int(t)
            if pred == 1 and truth == 1:
                tp += 1
            elif pred == 0 and truth == 0:
                tn += 1
            elif pred == 1 and truth == 0:
                fp += 1
            else:
                fn += 1

    total = tp + tn + fp + fn
    if total == 0:
        print("Skorlanacak etiketli satir yok. 'truth_occupied' dolduruldu mu?", file=sys.stderr)
        return 1

    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
    accuracy = (tp + tn) / total

    print(f"\n=== Slot doluluk metrikleri (pozitif=DOLU) ===")
    print(f"  ornek sayisi : {total} (atlanan/etiketsiz: {skipped})")
    print(f"  Confusion    : TP={tp}  FP={fp}  FN={fn}  TN={tn}")
    print(f"  Accuracy     : {accuracy * 100:5.1f}%")
    print(f"  Precision    : {precision * 100:5.1f}%")
    print(f"  Recall       : {recall * 100:5.1f}%")
    print(f"  F1-score     : {f1 * 100:5.1f}%")
    return 0


def main():
    ap = argparse.ArgumentParser(description="PARKOMFY slot doluluk dogrulugu")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("predict", help="videodan tahmin uret -> CSV")
    p.add_argument("--video", default=str(_ROOT / "loop1.mp4"))
    p.add_argument("--area", default="AREA-004")
    p.add_argument("--n", type=int, default=20, help="ornek kare sayisi")
    p.add_argument("--out", default="slots_pred.csv")
    p.add_argument("--save-frames", default=None, help="kareleri bu klasore yaz (etiketlemek icin)")
    p.set_defaults(func=cmd_predict)

    s = sub.add_parser("score", help="truth doldurulmus CSV'den metrik")
    s.add_argument("--csv", required=True)
    s.set_defaults(func=cmd_score)

    args = ap.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
