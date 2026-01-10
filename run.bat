@echo off
echo ====================================
echo PARKOMFY Application Starting...
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

REM Compile Java files
echo Compiling Java files...
if not exist out mkdir out
javac -d out -encoding UTF-8 src\main\java\com\parkomfy\model\*.java src\main\java\com\parkomfy\service\*.java src\main\java\com\parkomfy\repository\*.java src\main\java\com\parkomfy\gui\*.java src\main\java\com\parkomfy\*.java

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
