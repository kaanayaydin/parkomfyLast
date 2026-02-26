# YOLO gRPC Sunucusu (detection.proto)

Java PARKOMFY uygulamasının kullandığı `detection.proto` servisini **50051** portunda sunar.

## Kurulum

Proje kökünde sanal ortam kullanıyorsan:

```bash
cd /Users/ilyaskaan/Downloads/parkomfy-main
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r grpc_server/requirements.txt
```

Proto’dan Python kodu zaten üretilmiş (`detection_pb2.py`, `detection_pb2_grpc.py`). Yeniden üretmek istersen:

```bash
cd grpc_server
python generate.py
```

## Çalıştırma

```bash
cd /Users/ilyaskaan/Downloads/parkomfy-main/grpc_server
python3 server.py
```

Çıktı: `YOLO gRPC sunucusu dinleniyor: [::]:50051`

Bu sunucu açıkken Java uygulamasını (`mvn spring-boot:run`) ve fotoğraf testini (curl ile `/api/v1/detect/vehicle` veya `/detect/plate`) çalıştırabilirsin; artık **demo değil**, bu sunucudan gelen cevap kullanılacak.

## Davranış

- **Simülasyon modu:** Gerçek YOLO modeli yok; gelen görüntü boyutuna göre araç var/yok ve örnek plaka metni döner.
- Görüntü verisi **500 bayttan büyükse** → araç var (confidence 0.88), plaka örnek: `34 ABC 123`.
- İleride gerçek YOLO/OCR entegre edebilirsin; aynı `DetectVehicles` / `DetectLicensePlate` metodlarını kullanman yeterli.
