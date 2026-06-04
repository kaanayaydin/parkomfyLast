#!/bin/bash
# Tüm asset görsellerini sırayla slot_definer ile açar. Her görselde slotları etiketleyip 'q' ile kaydedin.
set -e
cd "$(dirname "$0")"
ASSETS="/Users/ilyaskaan/.cursor/projects/Users-ilyaskaan-Downloads-parkomfy-main/assets"
PY="./.venv/bin/python"

if [ ! -d "$ASSETS" ]; then
  echo "Assets klasörü bulunamadı: $ASSETS"
  exit 1
fi

i=1
for f in "$ASSETS"/WhatsApp_Image_2026-03-12*.png; do
  [ -f "$f" ] || continue
  echo ""
  echo "=== [$i] $f ==="
  "$PY" slot_definer.py "$f" -o "slots_$(printf '%02d' $i).json"
  i=$((i+1))
done
echo ""
echo "Bitti. Oluşan JSON'lar: slots_01.json, slots_02.json, ..."
