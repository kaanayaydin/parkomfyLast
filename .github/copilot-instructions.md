# PARKOMFY Copilot Instructions

## Project Overview

**PARKOMFY** is a Smart Parking Management System that uses YOLO-based computer vision (via security cameras) for real-time parking occupancy detection, combined with license plate recognition (OCR). It integrates MySQL database, Stripe payment processing, and a Swing GUI for monitoring and management.

**Technology Stack:**
- **Language:** Java 11+
- **Build Tool:** Maven
- **GUI:** Swing (javax.swing)
- **Database:** MySQL 8.0
- **Architecture Pattern:** Service Layer with Dependency Injection

---

## Architecture Overview

The codebase follows **strict layered OOP architecture**:

```
Model Layer (Domain)          → ParkingArea, Vehicle, LicensePlate, Payment, etc.
                                 (pure data models with getters/setters)
                               ↓
Service Layer (Business)      → ParkingService, PaymentService, DetectionService
Interface-based contracts     (implements I* interfaces, depends on repositories)
                               ↓
AI/OCR Layer (Specialized)    → YOLOInference (IYOLOInference), LicensePlateReader (ILicensePlateReader)
(swappable implementations)    (abstracts ML model backends: ONNX, Python API, DJL)
                               ↓
Repository Layer (Data Access) → DatabaseManager (implements IParkingRepository)
                                 (MySQL operations, simulated in demo)
```

### Key Design Patterns

1. **Dependency Injection:** Services receive dependencies via constructor (see `DetectionService`):
   - `new DetectionService(repository, yoloInference, licensePlateReader)`
   - Enables easy swapping of YOLO implementations (ONNX Runtime, Python REST, DJL)

2. **Interface-Based Abstraction:** All service layers use interfaces:
   - `IParkingService`, `IPaymentService`, `IDetectionService`
   - `IParkingRepository`, `IYOLOInference`, `ILicensePlateReader`
   - Never implement directly; always use interfaces as contract

3. **Encapsulation:** All fields are `private` with getters/setters:
   - See [src/main/java/com/parkomfy/model/Vehicle.java](src/main/java/com/parkomfy/model/Vehicle.java#L1)

---

## Critical Data Flows

### Parking Session Flow
1. **Entry:** `YOLOInference` detects vehicle → `LicensePlateReader` extracts plate
2. **Detection:** `DetectionService` orchestrates → stores in `IParkingRepository`
3. **Session:** `ParkingService.occupySlot()` creates `ParkingSession`, links `Vehicle` to `ParkingSlot`
4. **Exit:** `ParkingService.vacateSlot()` → `completeSession()` calculates fee
5. **Payment:** `PaymentService` processes via Stripe (configured in [src/main/java/com/parkomfy/service/PaymentService.java](src/main/java/com/parkomfy/service/PaymentService.java))

### System Communication Architecture

**Three-Tier Communication Pattern:**

```
Mobile/Client
    ↓ (RESTful API: HTTP/JSON)
Java Backend (Spring Boot/Servlet)
    ├─ ParkingService, PaymentService, DetectionService (interfaces + impls)
    ├─ REST Endpoints (API layer for mobile clients)
    ↓ (gRPC: Protobuf/Binary)
Python YOLO Service
    └─ Vehicle detection, Confidence scoring, Bounding boxes
```

### Detection Service Architecture
- **YOLOInference** (gRPC client) calls Python YOLO service
- **DetectionService** (business layer) orchestrates workflow with injected YOLOInference
- `DetectionService` handles parking logic; `YOLOInference` only manages gRPC calls to Python

**Implementation Pattern:**
1. Define `.proto` files for gRPC messages (vehicle detection, confidence, bbox)
2. Generate Java stubs with `protoc` compiler
3. `YOLOInference` implements `IYOLOInference` using gRPC stubs
4. Call Python service: `yoloStub.detectVehicles(request)` → returns `DetectionResponse`
5. `DetectionService` processes response → stores via `IParkingRepository`

See comments in [src/main/java/com/parkomfy/ai/YOLOInference.java](src/main/java/com/parkomfy/ai/YOLOInference.java#L8-L25) for implementation details.

---

## Project-Specific Conventions

### ID Generation
- **VehicleId:** `VEH-{timestamp}-{random}` (see [src/main/java/com/parkomfy/model/Vehicle.java](src/main/java/com/parkomfy/model/Vehicle.java#L26))
- **SessionId:** `SESSION-{timestamp}`
- **PaymentId:** `PAY-{timestamp}`
- Used consistently across all models

### Pricing Configuration
- `PricingPolicy` is mutable; set via `area.setPricingPolicy(policy)`
- Properties: `hourlyRate`, `firstHourRate`, `freeMinutes`, `maxDailyRate`
- Applied in `ParkingService.calculateFee()` during session completion

### Vehicle Types
- Enum-based: `CAR`, `MOTORCYCLE`, `TRUCK` (affects pricing)
- Stored in `Vehicle.vehicleType`

### Payment Methods
- `PaymentMethod` enum: `CREDIT_CARD`, `DEBIT_CARD`, `DIGITAL_WALLET`, `STRIPE`
- Stripe integration handled by `PaymentService`

---

## Build & Run Commands

### Maven Build
```bash
mvn clean package          # Compile, test, package JAR
mvn compile                # Just compile
mvn test                   # Run JUnit tests (if added)
```

### gRPC Code Generation
```bash
# Generate Java stubs from .proto files (before maven compile)
protoc --java_out=src/main/java --grpc-java_out=src/main/java src/main/proto/*.proto

# Or configure Maven plugin (add to pom.xml):
# <plugin>
#   <groupId>org.xolstice.maven.plugins</groupId>
#   <artifactId>protobuf-maven-plugin</artifactId>
#   <version>0.6.1</version>
# </plugin>
```

### Execution
**Recommended Windows:** Double-click `run.bat`

**Manual (Java backend only):**
```bash
# Requires: Java 11+ in PATH
mvn clean compile exec:java -Dexec.mainClass="com.parkomfy.ParkomfyApplication"

# Or run GUI:
mvn clean compile exec:java -Dexec.mainClass="com.parkomfy.gui.ParkomfyGUI"
```

**Full Stack (requires Python YOLO service running):**
```bash
# Terminal 1: Start Python YOLO gRPC server
python yolo_server.py --port=50051

# Terminal 2: Start Java backend
mvn clean compile exec:java -Dexec.mainClass="com.parkomfy.ParkomfyApplication"

# Terminal 3: Mobile/REST clients connect to http://localhost:8080
```

**Direct Java (if JAR built):**
```bash
java -cp target/parkomfy-1.0.0.jar:lib/* com.parkomfy.ParkomfyApplication
java -cp target/parkomfy-1.0.0.jar:lib/* com.parkomfy.gui.ParkomfyGUI
```

---

## Critical Integration Points

### REST API Layer (Mobile ↔ Java Backend)
- **Status:** To be implemented
- **Framework:** Spring Boot or Servlet-based REST endpoints
- **Endpoints:** `POST /api/parking/sessions`, `GET /api/parking/status`, `POST /api/payments`
- **Response Format:** JSON with standard HTTP status codes
- **Location to implement:** Create new `controller/` or `api/` package with REST controllers

### gRPC Communication (Java Backend ↔ Python YOLO Service)
- **Status:** To be configured
- **Protocol:** gRPC (Protobuf/Binary over HTTP/2)
- **Port:** Default `50051` (configurable)
- **Connection:** `YOLOInference` connects to Python server in constructor
- **Proto definitions:** Create `src/main/proto/` directory with `.proto` files:
  ```protobuf
  syntax = "proto3";
  
  message DetectionRequest {
    string camera_id = 1;
    bytes image_data = 2;
  }
  
  message DetectionResponse {
    repeated Vehicle vehicles = 1;
    double confidence = 2;
  }
  ```
- **Generate stubs:** Run `protoc` compiler before Maven build (see Build & Run section)
- **Implementation:** [src/main/java/com/parkomfy/ai/YOLOInference.java](src/main/java/com/parkomfy/ai/YOLOInference.java)

### Database (DatabaseManager)
- **Status:** Simulated (no real MySQL in current demo)
- **Connection:** `jdbc:mysql://localhost:3306/parkomfy` with hardcoded `root`/`password`
- **To activate:** Update connection in `ParkomfyApplication` constructor, ensure MySQL running
- **Key methods:** `saveArea()`, `getArea()`, `saveSession()`, `getSession()`, `savePayment()`

### Stripe Payment Integration
- **Location:** [src/main/java/com/parkomfy/service/PaymentService.java](src/main/java/com/parkomfy/service/PaymentService.java)
- **Current:** Simulated responses (no real Stripe API calls)
- **To activate:** Add Stripe Maven dependency, implement actual API calls using Stripe Java SDK

### GUI Components
- **Framework:** Swing (javax.swing) - not Spring MVC
- **Location:** [src/main/java/com/parkomfy/gui/ParkomfyGUI.java](src/main/java/com/parkomfy/gui/ParkomfyGUI.java)
- **Pattern:** Single-window frame with panels for monitoring, slot list, payment info
- **Updates:** `updateDisplay()` method refreshes real-time occupancy stats

---

## File Organization Reference

| Layer | Directory | Key Files |
|-------|-----------|-----------|
| **Models** | `model/` | `Vehicle.java`, `LicensePlate.java`, `ParkingArea.java`, `ParkingSession.java`, `Payment.java` |
| **Services** | `service/` | `ParkingService.java`, `PaymentService.java`, `DetectionService.java` (+ I* interfaces) |
| **AI/ML** | `ai/` | `YOLOInference.java`, `IYOLOInference.java` |
| **OCR** | `ocr/` | `LicensePlateReader.java`, `ILicensePlateReader.java` |
| **Data Access** | `repository/` | `DatabaseManager.java`, `IParkingRepository.java` |
| **GUI** | `gui/` | `ParkomfyGUI.java` (Swing-based) |
| **Main** | root | `ParkomfyApplication.java` (entry point, initializes all services) |

---

## Important Caveats for AI Agents

1. **gRPC Integration Pending:** `.proto` files not yet created. Must define message contracts for vehicle detection before generating Java stubs. See gRPC Communication section.

2. **REST API Not Implemented:** Mobile/client endpoints missing. Create `controller/` package with Spring/Servlet endpoints for parking, payment, and detection operations.

3. **Database is Simulated:** All repository operations print to console; no real data persisted. MySQL integration requires implementation.

4. **YOLO Model Not Loaded:** `YOLOInference` class awaits gRPC client implementation. Will connect to Python YOLO server (not load model in Java).

5. **Stripe Integration Incomplete:** `PaymentService` returns mock responses. Requires Stripe API key and real SDK calls.

6. **Single-Window GUI:** `ParkomfyGUI` is monolithic Swing frame. Adding new features requires editing that 577-line file.

7. **No Tests:** Only JUnit 4.13.2 in pom.xml scope=test; no test classes present. Writing tests will require creating `src/test/java/` structure.

8. **Maven Dependencies Missing:** gRPC libraries not yet in pom.xml. Add `grpc-netty-shaded`, `grpc-protobuf`, `protobuf-java` when ready.

---

## When Adding Features

- **New Services?** Create interface first (`IMyService.java`), then implementation. Inject via constructor.
- **New Models?** Add to `model/` with private fields, getters/setters, and sensible `toString()`.
- **Database Changes?** Update `DatabaseManager` and add methods to `IParkingRepository`.
- **GUI Enhancements?** Edit `ParkomfyGUI.java` or create new panels in separate files and add to main frame.
- **YOLO Backend?** Choose implementation, add Maven dependency, update `YOLOInference.initializeModel()` constructor.

Follow the existing OOP structure: **interfaces first, dependency injection, no magic numbers.**
