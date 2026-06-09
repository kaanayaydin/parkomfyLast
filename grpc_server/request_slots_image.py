#!/usr/bin/env python3
"""
Görsel dosyasını gRPC sunucusuna gönderir; DetectParkingSlotsImage yanıtındaki JPEG'i dosyaya yazar.
Kullanım:
  python request_slots_image.py "WhatsApp Image 2026-02-26 at 10.46.23.jpeg"
  python request_slots_image.py "WhatsApp Image 2026-02-26 at 10.46.23.jpeg" -o sonuc.jpg
"""
import argparse
import sys
import grpc
import detection_pb2
import detection_pb2_grpc

def main():
    p = argparse.ArgumentParser(description="Park slot görseli gönderir, çizilmiş JPEG döner.")
    p.add_argument("image_path", help="Görsel dosyası (örn. WhatsApp Image 2026-02-26 at 10.46.23.jpeg)")
    p.add_argument("-o", "--output", default=None, help="Çıktı JPEG dosyası (yoksa stdout)")
    p.add_argument("--host", default="localhost:50051", help="gRPC adresi")
    args = p.parse_args()

    with open(args.image_path, "rb") as f:
        image_data = f.read()

    channel = grpc.insecure_channel(args.host)
    stub = detection_pb2_grpc.YOLODetectionServiceStub(channel)
    req = detection_pb2.DetectionRequest(camera_id="curl", image_data=image_data)

    try:
        resp = stub.DetectParkingSlotsImage(req, timeout=60)
    except grpc.RpcError as e:
        print("gRPC hatası:", e.code(), e.details(), file=sys.stderr)
        return 1

    jpeg = resp.image_jpeg
    if not jpeg:
        print("Sunucu boş görsel döndü.", file=sys.stderr)
        return 1

    if args.output:
        with open(args.output, "wb") as f:
            f.write(jpeg)
        print("Yazıldı:", args.output)
    else:
        sys.stdout.buffer.write(jpeg)
    return 0

if __name__ == "__main__":
    sys.exit(main())
