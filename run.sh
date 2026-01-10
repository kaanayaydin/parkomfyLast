#!/bin/bash

echo "===================================="
echo "PARKOMFY Application Starting..."
echo "===================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 11 or higher"
    exit 1
fi

# Create output directory
mkdir -p out

# Compile Java files
echo "Compiling Java files..."
find src/main/java -name "*.java" > sources.txt
javac -d out -encoding UTF-8 @sources.txt
rm sources.txt

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed"
    exit 1
fi

echo ""
echo "Compilation successful!"
echo ""
echo "Running PARKOMFY Application..."
echo ""

# Run the application
java -cp out com.parkomfy.ParkomfyApplication
