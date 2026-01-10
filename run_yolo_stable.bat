@echo off
REM YOLO Server Stable Test Script
REM This script safely tests the YOLO detection server

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║      YOLO Detection Server - Stable Test                   ║
echo ║         (No GPU/CUDA Required - Pure Simulation)           ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

REM Check Python installation
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Python not found! Installing would be needed.
    echo Try: pip install -r requirements.txt
    exit /b 1
)
echo ✅ Python found
python --version
echo.

REM Check if port 50051 is already in use
echo Checking port 50051...
netstat -ano | findstr :50051 >nul 2>&1
if not errorlevel 1 (
    echo ⚠️  Port 50051 may already be in use
    echo Killing existing process...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :50051') do taskkill /PID %%a /F 2>nul
    timeout /t 2 /nobreak
)
echo ✅ Port 50051 ready
echo.

REM Start YOLO server
echo 🚀 Starting YOLO Detection Server...
python yolo_server_stable.py --host 127.0.0.1 --port 50051

REM If server stops, show log
if errorlevel 1 (
    echo.
    echo ❌ Server failed to start
    if exist yolo_server.log (
        echo.
        echo Recent log entries:
        type yolo_server.log
    )
    pause
    exit /b 1
)

pause
