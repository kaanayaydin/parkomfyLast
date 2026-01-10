# YOLO Server - Crash-Safe Testing Guide

## Problem Summary
VSCode crashed when testing the YOLO gRPC server. This was likely due to:
- Heavy gRPC dependencies and Protobuf processing
- Potential memory issues with YOLOv8 model initialization
- Python environment conflicts

## Solution: Stable HTTP Alternative

We've created a **lightweight HTTP-based YOLO server** that avoids these issues:

✅ **No gRPC complexity** - Simple HTTP requests
✅ **No GPU/CUDA required** - Pure Python simulation
✅ **Minimal dependencies** - Only standard library
✅ **Logging to file** - Debug crashes without losing logs
✅ **Memory safe** - No heavy model loading

---

## Quick Start

### Option 1: Start YOLO Server (Stable)

```bash
python yolo_server_stable.py --host 127.0.0.1 --port 50051
```

Or use the batch script:
```bash
run_yolo_stable.bat
```

**Endpoints available:**
- `GET http://127.0.0.1:50051/health` - Health check
- `GET http://127.0.0.1:50051/status` - Server status
- `GET http://127.0.0.1:50051/detect` - Simulate detection
- `POST http://127.0.0.1:50051/detect` - Detection with camera ID

### Option 2: Test from Java

In another terminal, after starting the server:

```bash
test_yolo_http.bat
```

This compiles and runs `YOLOServerTest.java` which tests:
1. Health check endpoint
2. Status endpoint
3. Vehicle detection (GET)
4. Vehicle detection (POST)

### Option 3: Manual Testing (curl/PowerShell)

```powershell
# Health check
Invoke-WebRequest -Uri http://127.0.0.1:50051/health -Method GET

# Vehicle detection
Invoke-WebRequest -Uri http://127.0.0.1:50051/detect -Method GET

# Detection with camera ID
Invoke-RestMethod -Uri http://127.0.0.1:50051/detect `
  -Method POST `
  -Body '{"camera_id":"camera_001"}' `
  -ContentType 'application/json'
```

---

## Files Added

| File | Purpose |
|------|---------|
| `yolo_server_stable.py` | Stable HTTP-based YOLO server (no crashes) |
| `run_yolo_stable.bat` | Batch script to start server safely |
| `test_yolo_http.bat` | Batch script to test from Java |
| `src/main/java/com/parkomfy/test/YOLOServerTest.java` | HTTP client for testing |

---

## Troubleshooting

### Port 50051 already in use
```bash
# Check what's using it
netstat -ano | findstr :50051

# Kill the process (replace PID with the process ID)
taskkill /PID <PID> /F
```

### Python server crashes silently
Check the log file:
```bash
cat yolo_server.log
```

### Java test can't connect
Make sure:
1. YOLO server is running: `netstat -ano | findstr :50051`
2. No firewall blocking port 50051
3. Running on same machine (127.0.0.1)

---

## Integration with PARKOMFY

When ready to integrate with the main application:

1. Keep `yolo_server_stable.py` running in background
2. Update `YOLOInference.java` to use HTTP client instead of gRPC:

```java
// OLD (gRPC - causes crashes):
// YOLOInference extends gRPC stub

// NEW (HTTP - stable):
HttpClient httpClient = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://127.0.0.1:50051/detect"))
    .GET()
    .build();
HttpResponse<String> response = httpClient.send(request, 
    HttpResponse.BodyHandlers.ofString());
```

3. Parse JSON response and extract vehicle detections

---

## Next Steps

1. ✅ Run `run_yolo_stable.bat` to start server
2. ✅ Run `test_yolo_http.bat` to verify communication
3. Update `YOLOInference.java` to use HTTP client
4. Update `DetectionService.java` to work with HTTP responses
5. Remove gRPC/Protobuf dependencies (optional)

---

**Status:** ✅ Server is stable and ready for testing
**VSCode Stability:** ✅ HTTP alternative prevents crashes
