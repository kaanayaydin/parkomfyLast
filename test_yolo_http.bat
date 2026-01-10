@echo off
REM YOLO Server HTTP Test from Java
REM Safe alternative to gRPC (no VSCode crashes)

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║      YOLO Server - HTTP Client Test (Java)                 ║
echo ║           Tests communication with Python server            ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

REM Check if YOLO server is running
echo Checking if YOLO server is running on port 50051...
netstat -ano | findstr :50051 >nul 2>&1
if errorlevel 1 (
    echo ❌ YOLO server not running!
    echo Please start it first with: run_yolo_stable.bat
    echo.
    pause
    exit /b 1
)
echo ✅ YOLO server is running
echo.

REM Check Java installation
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ Java not found!
    exit /b 1
)
echo ✅ Java found
echo.

REM Compile test
echo 📦 Compiling YOLOServerTest...
javac -encoding UTF-8 -d target\classes src\main\java\com\parkomfy\test\YOLOServerTest.java
if errorlevel 1 (
    echo ❌ Compilation failed!
    pause
    exit /b 1
)
echo ✅ Compilation successful
echo.

REM Run test
echo 🧪 Running HTTP client test...
java -cp target\classes com.parkomfy.test.YOLOServerTest

if errorlevel 1 (
    echo ❌ Test failed!
    pause
    exit /b 1
)

echo.
echo ✅ All HTTP tests passed!
echo.
pause
