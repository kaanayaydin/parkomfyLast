#!/usr/bin/env python3
"""50051'deki YOLO gRPC sunucusuna bağlanıp test isteği atar. Sunucu çalışıyorsa cevap yazar."""
import sys
import grpc
import detection_pb2
import detection_pb2_grpc

def main():
    try:
        channel = grpc.insecure_channel("localhost:50051")
        stub = detection_pb2_grpc.YOLODetectionServiceStub(channel)
        req = detection_pb2.DetectionRequest(camera_id="test", image_data=b"x" * 1000, slot_id="s1")
        resp = stub.DetectVehicles(req, timeout=60)
        print("OK - Sunucu yanıt verdi:", "vehicle_detected=", resp.vehicle_detected, "confidence=", resp.confidence)
        return 0
    except grpc.RpcError as e:
        print("HATA - Sunucuya ulaşılamadı:", e.code(), e.details(), file=sys.stderr)
        return 1
    except Exception as e:
        print("HATA:", e, file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
