#!/usr/bin/env python3
"""
Park slotu etiketleme (label image): Görselde her slot için 4 köşe tıklayın.
Sıra: sol-üst, sağ-üst, sağ-alt, sol-alt (veya saat yönünde).
Çıktı: YOLO segment formatında TXT + slotların çizili olduğu JPG (aynı ada .txt ve .jpg).

Kullanım:
  python slot_definer.py <görsel_dosyası> [-o labels.txt]
  python slot_definer.py ../assets/foto.png
  python slot_definer.py 2car_parked.jpeg -o labels_2car.txt
"""
import argparse
import cv2
import numpy
import os

current_slot_points = []
all_slots = []
image = None
image_draw = None  # Çizimler bu kopyada


def click_event(event, x, y, flags, param):
    global image_draw
    if event == cv2.EVENT_LBUTTONDOWN:
        current_slot_points.append((x, y))
        print(f"Nokta eklendi: {x}, {y} ({len(current_slot_points)}/4)")
        if len(current_slot_points) == 4:
            pts = numpy.array(current_slot_points, dtype=numpy.int32)
            cv2.polylines(image_draw, [pts], True, (0, 0, 255), 2)
            all_slots.append([list(p) for p in current_slot_points])
            current_slot_points.clear()
            print(f"Slot tanımlandı. Toplam slot: {len(all_slots)}")


def main():
    global image, image_draw
    p = argparse.ArgumentParser(description="Park slotlarını 4 köşe tıklayarak etiketle. Çıkmak için 'q'.")
    p.add_argument("image_path", help="Etiketlenecek görsel (örn. 2car_parked.jpeg veya assets/...png)")
    p.add_argument("-o", "--output", default=None, help="Çıktı TXT (YOLO format; yoksa görsel adına göre .txt)")
    p.add_argument("--class-id", type=int, default=0, help="YOLO class_id (varsayılan: 0)")
    args = p.parse_args()

    image_path = args.image_path
    if not os.path.isfile(image_path):
        print(f"Hata: Dosya bulunamadı: {image_path}")
        return 1

    image = cv2.imread(image_path)
    if image is None:
        print(f"Hata: Görsel yüklenemedi: {image_path}")
        return 1
    image_draw = image.copy()
    h, w = image.shape[:2]

    out_txt = args.output
    if out_txt is None:
        base = os.path.splitext(os.path.basename(image_path))[0]
        out_txt = f"{base}.txt"
    if not out_txt.endswith(".txt"):
        out_txt = out_txt.rstrip() + ".txt"

    cv2.namedWindow("Park - 4 köşe tıkla, q ile kaydet ve çık")
    cv2.setMouseCallback("Park - 4 köşe tıkla, q ile kaydet ve çık", click_event)

    print("Her slot için 4 köşe tıklayın (sol-üst, sağ-üst, sağ-alt, sol-alt). Bitince 'q' ile kaydedin.")
    while True:
        # Geçici noktaları göster
        disp = image_draw.copy()
        if current_slot_points:
            for i, pt in enumerate(current_slot_points):
                cv2.circle(disp, tuple(pt), 5, (0, 255, 0), -1)
            if len(current_slot_points) >= 2:
                cv2.polylines(disp, [numpy.array(current_slot_points)], False, (0, 255, 0), 1)
        cv2.imshow("Park - 4 köşe tıkla, q ile kaydet ve çık", disp)
        if cv2.waitKey(1) & 0xFF == ord("q"):
            break

    # YOLO segment format: her satır = class_id x1 y1 x2 y2 x3 y3 x4 y4 (normalize 0-1)
    with open(out_txt, "w", encoding="utf-8") as f:
        for slot in all_slots:
            norm = [slot[i][0] / w, slot[i][1] / h for i in range(4)]
            line = f"{args.class_id} " + " ".join(f"{x:.6g} {y:.6g}" for x, y in norm) + "\n"
            f.write(line)
    # Slotların çizili olduğu görseli JPG olarak kaydet (TXT ile aynı ada .jpg)
    out_jpg = out_txt[:-4] + ".jpg"
    cv2.imwrite(out_jpg, image_draw)
    print(f"Kaydedildi: {out_txt} (YOLO format, {len(all_slots)} slot)")
    print(f"Kaydedildi: {out_jpg} (slotlar çizili)")
    cv2.destroyAllWindows()
    return 0


if __name__ == "__main__":
    exit(main())
