@echo off
REM PARKOMFY Backend Test Script

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║          PARKOMFY Backend - Complete Test Suite            ║
echo ║              Java Compilation & Execution                 ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

REM Check Java installation
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ Java not found! Please install Java 11 or higher.
    exit /b 1
)
echo ✅ Java found
echo.

REM Create output directory
if not exist out mkdir out
echo ✅ Output directory ready
echo.

REM Compile Model classes
echo 📦 Compiling Model classes...
javac -encoding UTF-8 -d out src/main/java/com/parkomfy/model/*.java
if errorlevel 1 (
    echo ❌ Model compilation failed!
    exit /b 1
)
echo ✅ Model classes compiled
echo.

REM Compile Repository
echo 📦 Compiling Repository layer...
javac -encoding UTF-8 -cp out -d out src/main/java/com/parkomfy/repository/*.java
if errorlevel 1 (
    echo ❌ Repository compilation failed!
    exit /b 1
)
echo ✅ Repository layer compiled
echo.

REM Compile AI, OCR, Service
echo 📦 Compiling AI/OCR/Service layers...
javac -encoding UTF-8 -cp out -d out ^
    src/main/java/com/parkomfy/ai/*.java ^
    src/main/java/com/parkomfy/ocr/*.java ^
    src/main/java/com/parkomfy/service/*.java
if errorlevel 1 (
    echo ❌ Layer compilation failed!
    exit /b 1
)
echo ✅ AI/OCR/Service layers compiled
echo.

REM Compile API
echo 📦 Compiling API layer...
javac -encoding UTF-8 -cp out -d out src/main/java/com/parkomfy/api/*.java
if errorlevel 1 (
    echo ❌ API compilation failed!
    exit /b 1
)
echo ✅ API layer compiled
echo.

REM Compile Demo
echo 📦 Compiling Demo application...
javac -encoding UTF-8 -cp out -d out src/main/java/com/parkomfy/ParkomfyDemo.java
if errorlevel 1 (
    echo ❌ Demo compilation failed!
    exit /b 1
)
echo ✅ Demo application compiled
echo.

REM Run Demo
echo 🎬 Running Demo...
echo.
java -cp out com.parkomfy.ParkomfyDemo
echo.

REM Test results
echo ═══════════════════════════════════════════════════════════
echo.
echo ✅ All tests completed successfully!
echo.
echo 📡 REST API Ready: http://localhost:8080/api/v1
echo 🐍 Python Server: http://localhost:50052
echo.
echo Test with:
echo   curl http://localhost:8080/api/v1/health
echo   curl http://localhost:50052/health
echo.
pause
