#!/usr/bin/env python3
"""
Evaluate plate_reader accuracy on a folder of images.
Usage:
  python eval_plates.py --dir ./test_plates --labels labels.csv
labels.csv format: filename,expected_plate  (e.g. car1.jpg,34ABC123)
"""
import argparse
import csv
import sys
from pathlib import Path

import cv2

import plate_reader


def load_labels(path: Path) -> dict:
    labels = {}
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 2:
                continue
            labels[row[0].strip()] = plate_reader.validate_turkish_plate(row[1].strip()) or row[1].strip().upper()
    return labels


def main():
    parser = argparse.ArgumentParser(description="PARKOMFY plate OCR evaluation")
    parser.add_argument("--dir", required=True, help="Directory with plate images")
    parser.add_argument("--labels", required=True, help="CSV: filename,expected_plate")
    args = parser.parse_args()

    img_dir = Path(args.dir)
    labels = load_labels(Path(args.labels))
    if not labels:
        print("No labels loaded.", file=sys.stderr)
        sys.exit(1)

    correct = 0
    total = 0
    results = []

    for fname, expected in labels.items():
        path = img_dir / fname
        if not path.is_file():
            print(f"SKIP missing: {fname}")
            continue
        frame = cv2.imread(str(path))
        if frame is None:
            print(f"SKIP unreadable: {fname}")
            continue
        det = plate_reader.process_and_read_detailed(frame)
        predicted = det.get("text", "")
        conf = det.get("confidence", 0.0)
        norm_exp = plate_reader.validate_turkish_plate(expected) or plate_reader._normalize_raw(expected)
        norm_pred = plate_reader.validate_turkish_plate(predicted) or plate_reader._normalize_raw(predicted)
        match = norm_pred == norm_exp
        total += 1
        if match:
            correct += 1
        results.append((fname, expected, predicted, conf, match))
        status = "OK" if match else "MISS"
        print(f"{status} {fname}: expected={expected} got={predicted} conf={conf:.2f}")

    acc = (correct / total * 100) if total else 0.0
    print(f"\nAccuracy: {correct}/{total} = {acc:.1f}%")
    return 0 if total and correct == total else 1


if __name__ == "__main__":
    sys.exit(main())
