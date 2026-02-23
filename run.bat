@echo off
REM ========================================
REM PARKOMFY - Smart Parking Management
REM Build and Run Script
REM ========================================
echo.
echo ====================================
echo PARKOMFY Application Launcher
echo ====================================
echo.

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 11 or higher
    pause
    exit /b 1
)

REM Create output directory if it doesn't exist
if not exist out mkdir out

REM Compile all Java files
echo Compiling Java source files...
javac -d out -encoding UTF-8 ^
    src/main/java/com/parkomfy/*.java ^
    src/main/java/com/parkomfy/model/*.java ^
    src/main/java/com/parkomfy/service/*.java ^
    src/main/java/com/parkomfy/repository/*.java ^
    src/main/java/com/parkomfy/api/*.java ^
    src/main/java/com/parkomfy/controller/*.java ^
    src/main/java/com/parkomfy/ai/*.java ^
    src/main/java/com/parkomfy/ocr/*.java ^
    src/main/java/com/parkomfy/gui/*.java ^
    2>&1

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Compilation failed!
    echo Please check the error messages above
    pause
    exit /b 1
)

echo.
echo Compilation successful!
echo.
echo ====================================
echo Running PARKOMFY Application Demo
echo ====================================
echo.

REM Run the demo application
java -cp out com.parkomfy.ParkomfyDemo

pause

if %errorlevel% neq 0 (
    echo ERROR: Compilation failed
    pause
    exit /b 1
)

echo.
echo Compilation successful!
echo.
echo Running PARKOMFY Application...
echo.
echo Launching GUI Mode (default)...
echo To run in Console Mode, use: java -cp out com.parkomfy.ParkomfyApplication
echo.

REM Launch GUI directly
java -cp out com.parkomfy.gui.ParkomfyGUI

pause
