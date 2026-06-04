#!/bin/bash
# Park slotu tespiti: görseli API'ye gönderir, sonucu output_slots.jpg olarak kaydeder.
# Kullanım: ./detect_slots.sh [görsel_dosya]
# Örnek:   ./detect_slots.sh
#          ./detect_slots.sh "/Users/ilyaskaan/Downloads/WhatsApp Image 2026-02-26 at 10.46.23.jpeg"

DEFAULT_NAME="WhatsApp Image 2026-02-26 at 10.46.23.jpeg"
IMAGE="${1:-}"

if [ -z "$IMAGE" ]; then
  # Önce mevcut dizin, sonra Downloads
  if [ -f "$DEFAULT_NAME" ]; then
    IMAGE="$DEFAULT_NAME"
  elif [ -f "$HOME/Downloads/$DEFAULT_NAME" ]; then
    IMAGE="$HOME/Downloads/$DEFAULT_NAME"
  else
    echo "Dosya bulunamadı: $DEFAULT_NAME (mevcut dizin veya ~/Downloads kontrol edildi)"
    echo "Kullanım: ./detect_slots.sh \"<görsel_yolu>\""
    exit 1
  fi
fi

if [ ! -f "$IMAGE" ]; then
  echo "Dosya bulunamadı: $IMAGE"
  exit 1
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
echo "Gönderiliyor: $IMAGE"
echo "API: $BASE_URL/api/v1/detect/vehicle/image"

HTTP_CODE=$(curl -s -w "%{http_code}" -o output_slots.jpg -X POST \
  -F "image=@$IMAGE" \
  "$BASE_URL/api/v1/detect/vehicle/image")

if [ "$HTTP_CODE" != "200" ]; then
  echo "Hata: HTTP $HTTP_CODE"
  [ -s output_slots.jpg ] && head -c 500 output_slots.jpg | cat -v; echo
  exit 1
fi

if [ ! -s output_slots.jpg ]; then
  echo "Hata: Yanıt boş. (Spring Boot + gRPC sunucusu çalışıyor mu?)"
  exit 1
fi

echo "OK. Sonuç: output_slots.jpg"
