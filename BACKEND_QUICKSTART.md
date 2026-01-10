# PARKOMFY - Backend Başlatma ve Test Rehberi

## ✅ Kurulum Tamamlandı

Aşağıdaki dosyalar oluşturulmuş/güncellenmiştir:

### 1. **pom.xml** - Dependencies güncellenmiş
- ✅ Spring Boot 2.7.0
- ✅ gRPC 1.51.0
- ✅ Protobuf 3.21.4
- ✅ Jackson (JSON serialization)
- ✅ Protobuf Maven Plugin

### 2. **Spring Boot Uygulaması**
- ✅ `ParkomfySpringApplication.java` - Main class
- ✅ `ParkingRestController.java` - REST endpoints
- ✅ `application.properties` - Configuration

### 3. **Python gRPC Server**
- ✅ `yolo_server.py` - YOLO gRPC server (simulation mode)

---

## 🚀 Backend'i Başlatma

### Adım 1: IDE'de çalıştır (IntelliJ IDEA / VS Code)

**IntelliJ IDEA:**
1. `File → Open → pom.xml` seç
2. "Open as Project" tıkla
3. Maven projects yüklensin (sağ üstte notification)
4. `ParkomfySpringApplication.java` açıkça sağ tıkla
5. `Run 'ParkomfySpringApplication.main()'` seç

**VS Code:**
1. Extension: "Extension Pack for Java" kur
2. `ParkomfySpringApplication.java` açıkça
3. `▶ Run` butonuna tıkla

### Adım 2: Terminal ile çalıştır (eğer Maven/Java PATH'te varsa)

```bash
# Maven wrapper ile (eğer varsa)
./mvnw spring-boot:run

# Veya javac ile compile et, sonra çalıştır
javac -cp "src/main/java" src/main/java/com/parkomfy/ParkomfySpringApplication.java
java -cp "src/main/java" com.parkomfy.ParkomfySpringApplication
```

### Adım 3: run.bat ile (Windows)

```batch
@echo off
java -version >nul 2>&1
if errorlevel 1 (
    echo Java yüklü degil!
    exit /b 1
)

echo PARKOMFY Backend baslatiliyor...
cd /d "%~dp0"

REM Compile
javac -encoding UTF-8 -d out ^
    src/main/java/com/parkomfy/*.java ^
    src/main/java/com/parkomfy/**/*.java

REM Run Spring Boot
java -cp out;lib/* com.parkomfy.ParkomfySpringApplication
```

---

## 🐍 Python YOLO Server'ını Başlatma

### Terminal 1: Python Server

```bash
# Simulation mode (hızlı, hazır)
python yolo_server.py

# YOLO mode (gerçek model gerekli)
python yolo_server.py --yolo

# Custom port
python yolo_server.py --port 50051
```

**Beklenen çıktı:**
```
✅ YOLO gRPC Server Ready!
   Address: localhost:50051
   Mode: SIMULATION
   Listening for connections...
```

---

## 🧪 REST API'yi Test Etme

### Terminal 2: Test Requests

**Health Check:**
```bash
curl -X GET http://localhost:8080/api/v1/health
```

**Beklenen Response:**
```json
{
  "status": "UP",
  "service": "PARKOMFY",
  "timestamp": "2026-01-10T12:30:45.123456"
}
```

**Parking Status:**
```bash
curl -X GET "http://localhost:8080/api/v1/parking/status?areaId=AREA-001"
```

**Beklenen Response:**
```json
{
  "success": true,
  "data": {
    "areaId": "AREA-001",
    "areaName": "Özyeğin University Parking",
    "totalSlots": 20,
    "availableSlots": 18,
    "occupiedSlots": 2,
    "occupancyRate": 0.10,
    "lastUpdated": "2026-01-10T12:30:45.123456"
  },
  "message": "Status retrieved successfully",
  "statusCode": 200
}
```

**Yeni Araç Girişi:**
```bash
curl -X POST http://localhost:8080/api/v1/parking/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "areaId": "AREA-001",
    "licensePlate": "34ABC123",
    "vehicleType": "car"
  }'
```

**Boş Slotları Listele:**
```bash
curl -X GET "http://localhost:8080/api/v1/parking/slots/available?areaId=AREA-001"
```

**Aktif Oturumları Listele:**
```bash
curl -X GET http://localhost:8080/api/v1/parking/sessions/active
```

**Ödeme İşleme:**
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "SESSION-1234567890",
    "amount": 45.50,
    "paymentMethod": "CREDIT_CARD",
    "cardToken": "tok_visa_4242"
  }'
```

---

## 📊 Postman Collection Örneği

**File: `postman_collection.json`** (Elle oluştur veya import et)

```json
{
  "info": {
    "name": "PARKOMFY API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Health Check",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/v1/health"
      }
    },
    {
      "name": "Get Parking Status",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/api/v1/parking/status?areaId=AREA-001"
      }
    },
    {
      "name": "Create Session",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/v1/parking/sessions",
        "body": {
          "mode": "raw",
          "raw": "{\"areaId\":\"AREA-001\",\"licensePlate\":\"34ABC123\",\"vehicleType\":\"car\"}"
        }
      }
    }
  ]
}
```

---

## ✅ Success Indicators

### ✅ Backend Başarıyla Başladı
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v2.7.0)

2026-01-10 12:30:45.123 INFO ParkomfySpringApplication : Started ParkomfySpringApplication
Tomcat started on port(s): 8080
```

### ✅ Python Server Başarıyla Başladı
```
✅ YOLO gRPC Server Ready!
   Address: localhost:50051
   Mode: SIMULATION
   Listening for connections...
```

### ✅ API Çalışıyor
```
curl http://localhost:8080/api/v1/health
{"status":"UP","service":"PARKOMFY"}
```

---

## 🔧 Troubleshooting

### Problem: "Port 8080 already in use"
```bash
# Port'u değiştir (application.properties)
server.port=8081

# Veya Windows'ta port'u öldür
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Problem: "Cannot find symbol 'ParkingApiController'"
```bash
# Tüm Java dosyaları derlenmiş mi kontrol et
find src -name "*.java" -type f | wc -l
# En az 20+ dosya olması gerekir
```

### Problem: "gRPC connection refused"
```bash
# Python server çalışıyor mu kontrol et
netstat -an | findstr :50051

# YOLOInference'da gRPC mode'u devre dışı bırak
YOLOInference yolo = new YOLOInference();  // Simulation mode
```

---

## 📝 Next Steps

1. ✅ Backend test et (cURL/Postman)
2. ⏭️ Mobile app connect et (Flutter/React Native)
3. ⏭️ gRPC proto files derle (when ready)
4. ⏭️ Real YOLO model entegre et
5. ⏭️ MySQL database aktif et
6. ⏭️ Stripe payment'ı aktif et

---

## 📞 Contact & Support

- **Backend Port:** 8080 (http://localhost:8080)
- **gRPC Port:** 50051 (localhost:50051)
- **API Docs:** http://localhost:8080/api/v1/health

Sorularınız varsa documentation'ı kontrol edin:
- [API_IMPLEMENTATION_GUIDE.md](API_IMPLEMENTATION_GUIDE.md)
- [.github/copilot-instructions.md](.github/copilot-instructions.md)
