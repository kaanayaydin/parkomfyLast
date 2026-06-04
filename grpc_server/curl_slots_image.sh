#!/bin/bash
# Park slot görseli için gRPC çağrısı (grpcurl ile, curl benzeri).
# Kullanım: ./curl_slots_image.sh "WhatsApp Image 2026-02-26 at 10.46.23.jpeg"
# Çıktı: JSON (slot sayısı vs.) + isteğe bağlı -o out.jpg ile görsel kaydedilir.

set -e
IMAGE="${1:-WhatsApp Image 2026-02-26 at 10.46.23.jpeg}"
HOST="${2:-localhost:50051}"

if [ ! -f "$IMAGE" ]; then
  echo "Dosya bulunamadı: $IMAGE" >&2
  exit 1
fi

# Protobuf JSON: bytes alanı base64 olarak gönderilir
B64=$(base64 -i "$IMAGE" | tr -d '\n')
# Büyük görsellerde argüman sınırı aşılabilir; böyle durumda python kullan: python -c "..." veya request_slots_image.py

echo "DetectParkingSlotsImage çağrılıyor ($HOST)..." >&2
grpcurl -plaintext \
  -d "{\"camera_id\":\"curl\",\"image_data\":\"$B64\"}" \
  "$HOST" \
  com.parkomfy.grpc.YOLODetectionService/DetectParkingSlotsImage
