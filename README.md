# PARKOMFY – Smart Parking Management System

Real-time parking occupancy detection (YOLO via gRPC) and license plate recognition. Built with **Spring Boot**, **gRPC**, **MySQL**.

**Version:** 2.0 – Real CV inference (no simulation)

---

## Status

| Component        | Status |
|------------------|--------|
| Backend / REST API | Done |
| gRPC → Python YOLO | Done (blocking stub, localhost:50051) |
| IoU geometry validation | Done (SpatialAnalysisUtil, 0.5 threshold) |
| Multi-frame LPR (3 frames) | Done |
| CV failure logging (CVFailureLog) | Done |
| MySQL / Stripe / Auth | Ready for config / future |

---

## Architecture (short)

```
REST Client → Spring Boot API → Service Layer (Detection, Parking, Payment)
                    ↓
    DetectionService → YOLOInference (gRPC) → Python YOLO @ localhost:50051
                    → SpatialAnalysisUtil (IoU) → isValidGeometry
                    → LicensePlateReader (format validation)
                    → IParkingRepository → MySQL
```

- **YOLOInference:** Real gRPC calls (`DetectVehicles`, `DetectLicensePlate`). No `Math.random()`.
- **DetectionService:** IoU between detection box and slot (≥0.5 valid); `validatePlateWithMultiFrame` for 3-frame LPR; `CVFailureLog` for low confidence / bad geometry / OCR.
- **Proto:** `src/main/proto/detection.proto` – `YOLODetectionService`, `DetectionRequest/Response`, `BoundingBox`, `LicensePlateRequest/Response`.

---

## Project structure (main)

```
src/main/java/com/parkomfy/
├── ParkomfyApplication.java / ParkomfySpringApplication.java
├── api/          → ApiResponse, ParkingApiController, *Dto
├── controller/   → ParkingRestController
├── service/      → DetectionService, CVFailureLog, ParkingService, PaymentService
├── repository/   → IParkingRepository, DatabaseManager
├── model/        → ParkingSlot (slotWidth/slotHeight for IoU), Camera (currentFrame), DetectionResult, PlateReading, BoundingBoxDto, ...
├── ai/           → IYOLOInference, YOLOInference (gRPC client)
├── ocr/          → ILicensePlateReader, LicensePlateReader
├── util/         → SpatialAnalysisUtil (IoU, intersection area)
└── gui/          → ParkomfyGUI

src/main/proto/
└── detection.proto
```

---

## REST API (main endpoints)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/parking/status?areaId=...` | Occupancy status |
| POST | `/api/v1/parking/sessions` | Create session |
| GET | `/api/v1/parking/sessions/{id}` | Session details |
| POST | `/api/v1/parking/sessions/{id}/exit` | Exit and fee |
| GET | `/api/v1/parking/slots/available?areaId=...` | Available slots |
| POST | `/api/v1/payments` | Process payment |
| GET | `/api/v1/health` | Health check |

---

## Run

```bash
# Java (generates gRPC stubs from proto)
mvn clean compile
java -cp target/classes com.parkomfy.ParkomfySpringApplication   # REST :8080

# Python gRPC inference engine (prototype CV logic)
cd grpc_server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python json_to_pkl.py          # optional: slots_*.json -> slots_*.pkl
python server.py               # listens localhost:50051

# Mobile (Expo)
cd frontend && npm install && npx expo start
```

**Stack:** Java owns business logic + REST; Python is RPC-only (YOLO + EasyOCR + `slots_*.pkl` polygons). Frontend calls `http://localhost:8080/api/v1/*`, not Flask 5001.

---

## Tech stack

- Java 11, Maven  
- Spring Boot 2.x, REST/JSON  
- gRPC / Protobuf (YOLO service)  
- MySQL 8 (repository ready)  
- Swing (demo GUI)

---

## Team & License

Özyeğin University CS401-402 – İlyas Kaan Ayaydın, Yusuf Eren Erişmiş.  
Özyeğin University Academic License.

**Repo:** https://github.com/erenerismis/parkomfy

---

## Terminalden çalıştırma (Windows)

Projeyi ayağa kaldırmak için **4 ayrı terminal** açın. Proje kökü: `parkomfyLast-main`

### 1) MySQL

```powershell
cd db
.\start-mysql.bat
```

Veritabanı: `parkomfy` · kullanıcı: `root` · şifre: `password` (varsayılan)

### 2) Python — gRPC + kamera simülatörü

```powershell
cd grpc_server
pip install -r requirements.txt
$env:PARKOMFY_SLOT_MODEL = "C:\Users\erena\repos\parkomfyLast-main\best (2).pt"
python server.py
```

- gRPC: `localhost:50051`
- Kamera HTTP: `localhost:50052` (`loop1` / `loop2` / `loop3`, `giris`, `cikis`)
- Kök dizinde `loop1.mp4`, `loop2.mp4`, `loop3.mp4` ve `best (2).pt` dosyaları olmalı (Git’e dahil değil)

### 3) Spring Boot (REST API + admin)

```powershell
cd C:\Users\erena\repos\parkomfyLast-main
mvn spring-boot:run
```

- API: http://localhost:8080/api/v1/health
- Admin panel: http://localhost:8080/admin
- Admin giriş: `admin` / `1234` (veya `PARKOMFY_ADMIN_PASSWORD`)

### 4) Mobil uygulama (Expo)

```powershell
cd frontend
npm install
npx expo start
```

Telefondan bağlanırken `frontend/src/config/api.js` içindeki `API_HOST_OVERRIDE` değerini bilgisayarınızın yerel IP’sine ayarlayın (ör. `192.168.1.101`).

### Hızlı kontrol

| Servis        | Adres |
|---------------|--------|
| Spring Boot   | http://localhost:8080 |
| Admin panel   | http://localhost:8080/admin |
| gRPC (Python) | localhost:50051 |
| Kamera HTTP   | http://127.0.0.1:50052/snapshot/loop1.jpg |
| MySQL         | localhost:3306 |

### İlk kurulum (bir kez)

```powershell
# Java bağımlılıkları + proto stub
mvn clean compile -DskipTests

# MySQL şema (gerekirse)
# db/schema.sql dosyasını MySQL'e import edin
```

### Test hesapları

| Hesap | Rol | Şifre |
|-------|-----|-------|
| `admin` | Yönetici (web + mobil admin modu) | `1234` |
| `erenersms` | Sürücü (mobil) | (kayıtlı şifre) |
| `test@parkomfy.com` | Sürücü | (kayıtlı şifre) |
