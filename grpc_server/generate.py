#!/usr/bin/env python3
"""Generate Python gRPC code from detection.proto"""
import os
import subprocess
import sys

def main():
    root = os.path.dirname(os.path.abspath(__file__))
    result = subprocess.run([
        sys.executable, "-m", "grpc_tools.protoc",
        "-I.", "--python_out=.", "--grpc_python_out=.",
        "detection.proto"
    ], cwd=root)
    sys.exit(result.returncode)

if __name__ == "__main__":
    main()
