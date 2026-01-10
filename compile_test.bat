@echo off
REM PARKOMFY Complete Compilation Test

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║          PARKOMFY - Full Compilation Test                  ║
echo ║           (Testing all fixed classes)                      ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

REM Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ Java not found!
    exit /b 1
)
echo ✅ Java found
echo.

REM Create output directory
if not exist target\classes mkdir target\classes
echo ✅ Output directory ready
echo.

REM Compile all Java files
echo 📦 Compiling all Java source files...
echo.

REM Find all Java files and compile
for /r src\main\java %%f in (*.java) do (
    echo   Compiling: %%f
)
echo.

REM Compile with javac
javac -encoding UTF-8 -d target\classes -sourcepath src\main\java ^
  src\main\java\com\parkomfy\*.java ^
  src\main\java\com\parkomfy\model\*.java ^
  src\main\java\com\parkomfy\service\*.java ^
  src\main\java\com\parkomfy\repository\*.java ^
  src\main\java\com\parkomfy\api\*.java ^
  src\main\java\com\parkomfy\controller\*.java ^
  src\main\java\com\parkomfy\ai\*.java ^
  src\main\java\com\parkomfy\ocr\*.java ^
  src\main\java\com\parkomfy\gui\*.java ^
  src\main\java\com\parkomfy\test\*.java 2>&1

if errorlevel 1 (
    echo.
    echo ❌ Compilation FAILED!
    echo.
    pause
    exit /b 1
)

echo.
echo ✅ Compilation SUCCESSFUL!
echo.
echo ═══════════════════════════════════════════════════════════
echo Summary:
echo ─────────────────────────────────────────────────────────
echo ✓ All classes compiled without errors
echo ✓ No type mismatches
echo ✓ All imports resolved
echo ═══════════════════════════════════════════════════════════
echo.
pause
