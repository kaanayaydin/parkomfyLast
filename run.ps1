Write-Host "====================================" -ForegroundColor Cyan
Write-Host "PARKOMFY Application Starting..." -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

# Check if Java is installed
try {
    $javaVersion = java -version 2>&1
    Write-Host "Java found!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Java is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Please install Java 11 or higher from: https://www.oracle.com/java/technologies/downloads/" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

# Create output directory
if (-not (Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

# Compile Java files
Write-Host "Compiling Java files..." -ForegroundColor Yellow

$javaFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$javacArgs = @("-d", "out", "-encoding", "UTF-8") + $javaFiles.FullName

& javac $javacArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Compilation failed" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Compilation successful!" -ForegroundColor Green
Write-Host ""
Write-Host "Running PARKOMFY Application..." -ForegroundColor Yellow
Write-Host ""

# Run the application
java -cp out com.parkomfy.ParkomfyApplication

Write-Host ""
Read-Host "Press Enter to exit"
