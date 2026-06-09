#!/usr/bin/env python3
"""
Mevcut slots_XX.json dosyalarını YOLO segment formatında TXT'ye + slotların çizili JPG'ye dönüştürür.
Kullanım:
  python json_to_yolo_txt.py --img-dir ../assets
"""
import argparse
import json
import os
import glob
import numpy

def main():
    p = argparse.ArgumentParser(description="slots_XX.json -> YOLO formatında .txt")
    p.add_argument("--json-dir", default=".", help="JSON dosyalarının bulunduğu dizin")
    p.add_argument("--img-dir", default=None, help="Görsellerin dizini (boyut için); yoksa json-dir")
    p.add_argument("--class-id", type=int, default=0)
    args = p.parse_args()
    json_dir = os.path.abspath(args.json_dir)
    img_dir = args.img_dir or json_dir
    img_dir = os.path.abspath(img_dir)

    # Assets'teki WhatsApp görselleri sıralı (run_label_all.sh ile aynı sıra)
    asset_imgs = sorted(glob.glob(os.path.join(img_dir, "WhatsApp_Image_2026-03-12*.png")))
    if not asset_imgs:
        asset_imgs = sorted(glob.glob(os.path.join(img_dir, "*.png")) + glob.glob(os.path.join(img_dir, "*.jpg")) + glob.glob(os.path.join(img_dir, "*.jpeg")))

    for i in range(1, 20):
        json_path = os.path.join(json_dir, f"slots_{i:02d}.json")
        if not os.path.isfile(json_path):
            continue
        with open(json_path, "r", encoding="utf-8") as f:
            slots = json.load(f)
        if not slots:
            continue
        # Görsel boyutu: aynı numaralı asset veya json ile aynı isimde .txt için w,h gerek
        img_path = asset_imgs[i - 1] if i <= len(asset_imgs) else None
        w, h = None, None
        img = None
        if img_path and os.path.isfile(img_path):
            try:
                import cv2
                img = cv2.imread(img_path)
                if img is not None:
                    h, w = img.shape[:2]
            except Exception:
                pass
        if w is None or h is None:
            all_xy = [p for s in slots for p in s]
            w = max(p[0] for p in all_xy) + 10
            h = max(p[1] for p in all_xy) + 10
            if not (img_path and os.path.isfile(img_path)):
                print(f"Uyarı: {json_path} için görsel bulunamadı, tahmini boyut {w}x{h}")
        out_txt = os.path.join(json_dir, f"slots_{i:02d}.txt")
        with open(out_txt, "w", encoding="utf-8") as f:
            for slot in slots:
                norm = [[slot[j][0] / w, slot[j][1] / h] for j in range(4)]
                line = f"{args.class_id} " + " ".join(f"{x:.6g} {y:.6g}" for x, y in norm) + "\n"
                f.write(line)
        out_jpg = os.path.join(json_dir, f"slots_{i:02d}.jpg")
        if img is not None:
            import cv2
            draw = img.copy()
            for slot in slots:
                pts = numpy.array(slot, dtype=numpy.int32)
                cv2.polylines(draw, [pts], True, (0, 0, 255), 2)
            cv2.imwrite(out_jpg, draw)
            print(f"{json_path} -> {out_txt}, {out_jpg} ({len(slots)} slot, {w}x{h})")
        else:
            print(f"{json_path} -> {out_txt} ({len(slots)} slot, {w}x{h}, JPG yok)")
    return 0

if __name__ == "__main__":
    exit(main())
