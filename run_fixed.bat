@echo off
echo ====================================
echo PARKOMFY Application Starting...
echo ====================================
echo.

REM Try to find Java
set JAVA_CMD=java
where java >nul 2>&1
if %errorlevel% neq 0 (
    REM Try common Java locations
    if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
        set JAVA_CMD="C:\Program Files\Java\jdk-17\bin\java.exe"
        set JAVAC_CMD="C:\Program Files\Java\jdk-17\bin\javac.exe"
    ) else if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set JAVA_CMD="C:\Program Files\Java\jdk-21\bin\java.exe"
        set JAVAC_CMD="C:\Program Files\Java\jdk-21\bin\javac.exe"
    ) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.9-hotspot\bin\java.exe" (
        set JAVA_CMD="C:\Program Files\Eclipse Adoptium\jdk-17.0.10.9-hotspot\bin\java.exe"
        set JAVAC_CMD="C:\Program Files\Eclipse Adoptium\jdk-17.0.10.9-hotspot\bin\javac.exe"
    ) else (
        echo ERROR: Java is not installed or not in PATH
        echo.
        echo Please install Java 11 or higher:
        echo 1. Download from: https://adoptium.net/temurin/releases/?version=17
        echo 2. Install with "Add to PATH" option checked
        echo 3. Restart Command Prompt and try again
        echo.
        pause
        exit /b 1
    )
) else (
    set JAVAC_CMD=javac
)

REM Check Java version
echo Checking Java installation...
%JAVA_CMD% -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java found but cannot execute
    echo Please check Java installation
    pause
    exit /b 1
)

echo Java found! Continuing...
echo.

REM Create output directory
if not exist out mkdir out

REM Compile Java files
echo Compiling Java files...
%JAVAC_CMD% -d out -encoding UTF-8 src\main\java\com\parkomfy\model\*.java src\main\java\com\parkomfy\service\*.java src\main\java\com\parkomfy\repository\*.java src\main\java\com\parkomfy\*.java

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Compilation failed
    echo Please check the error messages above
    pause
    exit /b 1
)

echo.
echo Compilation successful!
echo.
echo Running PARKOMFY Application...
echo.

REM Run the application
%JAVA_CMD% -cp out com.parkomfy.ParkomfyApplication

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Application failed to run
    pause
    exit /b 1
)

echo.
pause
