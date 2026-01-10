# PARKOMFY API Implementation Guide

## Genel Bakış

PARKOMFY sistemi 2 ana API'ye sahiptir:

1. **REST API** - Mobil istemcileri ↔ Java Backend arasında (HTTP/JSON)
2. **gRPC API** - Java Backend ↔ Python YOLO Service arasında (Protobuf/Binary)

---

## 1. REST API Implementation

### 1.1 Endpoint'ler

| HTTP Method | Endpoint | Fonksiyon | Request | Response |
|---|---|---|---|---|
| GET | `/api/v1/parking/status` | Alan durumunu al | areaId | ParkingStatusDto |
| POST | `/api/v1/parking/sessions` | Yeni oturum oluştur | CreateSessionRequest | ParkingSessionDto |
| GET | `/api/v1/parking/sessions/{sessionId}` | Oturum detaylarını al | - | ParkingSessionDto |
| POST | `/api/v1/parking/sessions/{sessionId}/exit` | Araç çıkışını tamamla | - | PaymentDto |
| GET | `/api/v1/parking/slots/available` | Boş slotları listele | areaId | List<ParkingSlotDto> |
| GET | `/api/v1/parking/sessions/active` | Aktif oturumları listele | - | List<ParkingSessionDto> |
| POST | `/api/v1/payments` | Ödeme işle | PaymentRequest | PaymentResultDto |
| GET | `/api/v1/payments/{paymentId}` | Ödeme detaylarını al | - | PaymentDto |

### 1.2 Kullanım Örneği (cURL)

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

### 1.3 Servlet/Spring Boot Implementasyonu

#### Opsiyon 1: Servlet ile (Minimal Setup)

```java
// src/main/java/com/parkomfy/servlet/ParkingServlet.java
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.google.gson.Gson;

@WebServlet("/api/v1/parking/status")
public class ParkingServlet extends HttpServlet {
    private ParkingApiController controller;
    private Gson gson = new Gson();
    
    @Override
    public void init() {
        // Initialize controller with services
        IParkingRepository repository = new DatabaseManager(...);
        IParkingService parkingService = new ParkingService(repository, ...);
        controller = new ParkingApiController(parkingService, ...);
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        resp.setContentType("application/json");
        
        String areaId = req.getParameter("areaId");
        ApiResponse<ParkingStatusDto> response = controller.getParkingStatus(areaId);
        
        resp.getWriter().write(gson.toJson(response));
    }
}
```

#### Opsiyon 2: Spring Boot ile (Recommended)

**pom.xml'e ekle:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>2.7.0</version>
</dependency>
```

**Spring Controller Sınıfı:**
```java
// src/main/java/com/parkomfy/controller/ParkingRestController.java
package com.parkomfy.controller;

import org.springframework.web.bind.annotation.*;
import com.parkomfy.api.*;

@RestController
@RequestMapping("/api/v1")
public class ParkingRestController {
    
    private final ParkingApiController controller;
    
    public ParkingRestController(ParkingApiController controller) {
        this.controller = controller;
    }
    
    @GetMapping("/parking/status")
    public ApiResponse<ParkingStatusDto> getParkingStatus(@RequestParam String areaId) {
        return controller.getParkingStatus(areaId);
    }
    
    @PostMapping("/parking/sessions")
    public ApiResponse<ParkingSessionDto> createSession(
            @RequestBody CreateSessionRequest request) {
        return controller.createParkingSession(request);
    }
    
    @GetMapping("/parking/sessions/{sessionId}")
    public ApiResponse<ParkingSessionDto> getSession(@PathVariable String sessionId) {
        return controller.getSession(sessionId);
    }
    
    @PostMapping("/parking/sessions/{sessionId}/exit")
    public ApiResponse<PaymentDto> exitParking(@PathVariable String sessionId) {
        return controller.exitParking(sessionId);
    }
    
    @PostMapping("/payments")
    public ApiResponse<PaymentResultDto> processPayment(@RequestBody PaymentRequest request) {
        return controller.processPayment(request);
    }
    
    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentDto> getPayment(@PathVariable String paymentId) {
        return controller.getPayment(paymentId);
    }
    
    @GetMapping("/parking/slots/available")
    public ApiResponse<java.util.List<ParkingSlotDto>> getAvailableSlots(
            @RequestParam String areaId) {
        return controller.getAvailableSlots(areaId);
    }
    
    @GetMapping("/parking/sessions/active")
    public ApiResponse<java.util.List<ParkingSessionDto>> getActiveSessions() {
        return controller.getActiveSessions();
    }
}
```

### 1.4 Configuration Sınıfı (Spring Boot)

```java
// src/main/java/com/parkomfy/config/AppConfig.java
package com.parkomfy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.parkomfy.api.*;
import com.parkomfy.repository.*;
import com.parkomfy.service.*;

@Configuration
public class AppConfig {
    
    @Bean
    public IParkingRepository parkingRepository() {
        return new DatabaseManager(
            "jdbc:mysql://localhost:3306/parkomfy",
            "root",
            "password"
        );
    }
    
    @Bean
    public IPaymentService paymentService(IParkingRepository repository) {
        return new PaymentService(repository);
    }
    
    @Bean
    public IParkingService parkingService(IParkingRepository repository, 
                                         IPaymentService paymentService) {
        return new ParkingService(repository, paymentService);
    }
    
    @Bean
    public IDetectionService detectionService(IParkingRepository repository) {
        return new DetectionService(repository);
    }
    
    @Bean
    public ParkingApiController parkingApiController(
            IParkingService parkingService,
            IPaymentService paymentService,
            IDetectionService detectionService,
            IParkingRepository repository) {
        return new ParkingApiController(parkingService, paymentService, 
                                       detectionService, repository);
    }
}
```

---

## 2. gRPC API Implementation

### 2.1 Proto Dosyasını Derleme

**Step 1: Proto dosyalarını oluştur**
```bash
# Dosya: src/main/proto/detection.proto
# (Zaten oluşturulmuş, bak: .github/copilot-instructions.md)
```

**Step 2: Protobuf derleyicisini kur**

Windows:
```bash
# https://github.com/protocolbuffers/protobuf/releases adresinden indir
# protoc.exe'yi PATH'e ekle
```

**Step 3: Java stubs üret**

```bash
# Terminal'de çalıştır
protoc --java_out=src/main/java \
       --grpc-java_out=src/main/java \
       --plugin=protoc-gen-grpc-java=<path-to-grpc-java-plugin> \
       src/main/proto/detection.proto
```

### 2.2 Maven Setup

**pom.xml'e ekle:**
```xml
<properties>
    <grpc.version>1.51.0</grpc.version>
    <protobuf.version>3.21.4</protobuf.version>
</properties>

<dependencies>
    <!-- gRPC -->
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-netty-shaded</artifactId>
        <version>${grpc.version}</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-protobuf</artifactId>
        <version>${grpc.version}</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-stub</artifactId>
        <version>${grpc.version}</version>
    </dependency>
    
    <!-- Protobuf -->
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>${protobuf.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>
                    com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}
                </protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>
                    io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}
                </pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                        <goal>compile-custom</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 2.3 gRPC Client Implementation (YOLOInference)

**Şuanki yapı:**
```java
// src/main/java/com/parkomfy/ai/YOLOInference.java

public YOLOInference(String grpcHost, int grpcPort) {
    this.grpcHost = grpcHost;
    this.grpcPort = grpcPort;
    this.useGrpc = true;
    initializeModel();
}

private void initializeModel() {
    try {
        channel = ManagedChannelBuilder
            .forAddress(grpcHost, grpcPort)
            .usePlaintext()
            .build();
        // gRPC stub oluştur
    } catch (Exception e) {
        throw new RuntimeException("gRPC initialization failed", e);
    }
}
```

**Proto derlendikten sonra complete implementasyon:**
```java
@Override
public boolean detectVehicle(Camera camera, ParkingSlot slot) {
    try {
        // Build gRPC request
        DetectionRequest request = DetectionRequest.newBuilder()
            .setCameraId(camera.getCameraId())
            .setSlotId(slot.getSlotId())
            .setImageData(ByteString.copyFrom(frameData))
            .build();
        
        // Call gRPC service (synchronous)
        DetectionResponse response = stub.detectVehicles(request);
        
        // Extract results
        boolean detected = response.getVehicleDetected();
        lastConfidence = response.getConfidence();
        
        return detected;
    } catch (Exception e) {
        throw new RuntimeException("Vehicle detection failed", e);
    }
}
```

### 2.4 Python gRPC Server Örneği

**Python gereksinimler:**
```bash
pip install grpcio grpcio-tools ultralytics opencv-python numpy
```

**Python server (yolo_server.py):**
```python
import grpc
from concurrent import futures
import detection_pb2
import detection_pb2_grpc
from ultralytics import YOLO
import cv2
import numpy as np

class YOLODetectionServicer(detection_pb2_grpc.YOLODetectionServiceServicer):
    
    def __init__(self):
        self.model = YOLO("yolov8n.pt")  # Nano model
        
    def DetectVehicles(self, request, context):
        try:
            # Convert image data
            image_array = np.frombuffer(request.image_data, np.uint8)
            frame = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
            
            # Run YOLO inference
            results = self.model(frame)
            detections = results[0]
            
            # Extract vehicles (class 2 = car)
            vehicle_detected = False
            confidence = 0.0
            
            for detection in detections.boxes:
                if int(detection.cls) == 2:  # Vehicle class
                    vehicle_detected = True
                    confidence = float(detection.conf)
                    break
            
            return detection_pb2.DetectionResponse(
                vehicle_detected=vehicle_detected,
                confidence=confidence
            )
        except Exception as e:
            context.set_details(str(e))
            context.set_code(grpc.StatusCode.INTERNAL)
            return detection_pb2.DetectionResponse()
    
    def DetectLicensePlate(self, request, context):
        # License plate detection logic
        pass

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    detection_pb2_grpc.add_YOLODetectionServiceServicer_to_server(
        YOLODetectionServicer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    print("gRPC server running on port 50051")
    server.wait_for_termination()

if __name__ == '__main__':
    serve()
```

---

## 3. Integration Checklist

### REST API Setup
- [ ] Servlet/Spring Boot dependency'lerini ekle
- [ ] ParkingApiController'ı initialize et
- [ ] REST Endpoints'leri configure et
- [ ] JSON serialization'ı konfigure et (Gson/Jackson)
- [ ] Error handling'i test et

### gRPC Setup
- [ ] Proto dosyasını derleme (`protoc` komutu)
- [ ] gRPC Maven dependencies'lerini ekle
- [ ] YOLOInference'da gRPC stub'ı initialize et
- [ ] Python server'ı başlat (`python yolo_server.py`)
- [ ] Bağlantı testini yap

### Testing
- [ ] REST endpoints'lerini test et (Postman/cURL)
- [ ] gRPC communication'ını test et
- [ ] End-to-end flow'u test et (giriş → algılama → ödeme)

---

## 4. Run Commands

### Full Stack Çalıştırma

**Terminal 1: Python YOLO Service**
```bash
python yolo_server.py --port=50051
```

**Terminal 2: Java Backend**
```bash
# Spring Boot
mvn spring-boot:run

# Or direct execution
mvn clean compile exec:java -Dexec.mainClass="com.parkomfy.ParkomfyApplication"
```

**Terminal 3: Test Requests**
```bash
# Test REST API
curl http://localhost:8080/api/v1/parking/status?areaId=AREA-001

# Test gRPC connection (requires grpcurl)
grpcurl -plaintext localhost:50051 list
```
