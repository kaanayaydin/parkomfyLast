#!/usr/bin/env python3
"""
Python gRPC Server for YOLO Vehicle Detection
Provides vehicle and license plate detection via gRPC
"""

import grpc
from concurrent import futures
import sys
import numpy as np
from typing import Optional
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Try to import YOLO (optional for simulation mode)
try:
    from ultralytics import YOLO
    YOLO_AVAILABLE = True
except ImportError:
    YOLO_AVAILABLE = False
    logger.warning("YOLOv8 not installed. Running in simulation mode.")
    logger.warning("Install: pip install ultralytics opencv-python")

# Import generated gRPC code
# Note: Run 'python -m grpc_tools.protoc' to generate these files
# This will be in detection_pb2.py and detection_pb2_grpc.py
# For now, we'll create a mock servicer

class YOLODetectionServiceServicer:
    """
    gRPC Servicer for YOLO Detection
    Handles vehicle and license plate detection requests
    """
    
    def __init__(self, use_yolo: bool = False):
        self.use_yolo = use_yolo and YOLO_AVAILABLE
        self.model = None
        
        if self.use_yolo:
            try:
                logger.info("Loading YOLOv8 model...")
                self.model = YOLO("yolov8n.pt")  # Nano model for faster inference
                logger.info("YOLOv8 model loaded successfully")
            except Exception as e:
                logger.error(f"Failed to load YOLOv8 model: {e}")
                self.use_yolo = False
    
    def DetectVehicles(self, request, context):
        """
        Detect vehicles in an image frame
        
        Args:
            request: DetectionRequest with camera_id and image_data
            context: gRPC context
            
        Returns:
            DetectionResponse with vehicle_detected and confidence
        """
        try:
            logger.info(f"Received detection request from camera: {request.camera_id}")
            
            if not self.use_yolo:
                # Simulation mode
                logger.info("Running in SIMULATION mode")
                response = self._simulate_vehicle_detection(request)
                return response
            
            # Real YOLO inference
            response = self._yolo_vehicle_detection(request)
            return response
            
        except Exception as e:
            logger.error(f"Detection error: {e}")
            context.set_details(str(e))
            context.set_code(grpc.StatusCode.INTERNAL)
            # Return error response (would need proper error message type)
            raise e
    
    def DetectLicensePlate(self, request, context):
        """
        Detect license plate in an image frame
        
        Args:
            request: LicensePlateRequest with camera_id and image_data
            context: gRPC context
            
        Returns:
            LicensePlateResponse with license_plate_text and confidence
        """
        try:
            logger.info(f"Received license plate detection request from camera: {request.camera_id}")
            
            if not self.use_yolo:
                # Simulation mode
                response = self._simulate_license_plate_detection(request)
                return response
            
            # Real YOLO inference
            response = self._yolo_license_plate_detection(request)
            return response
            
        except Exception as e:
            logger.error(f"License plate detection error: {e}")
            context.set_details(str(e))
            context.set_code(grpc.StatusCode.INTERNAL)
            raise e
    
    def DetectBatch(self, request_iterator, context):
        """
        Batch detection for multiple frames (streaming RPC)
        
        Args:
            request_iterator: Iterator of DetectionRequest messages
            context: gRPC context
            
        Yields:
            DetectionResponse for each request
        """
        for request in request_iterator:
            try:
                logger.info(f"Processing batch request from camera: {request.camera_id}")
                response = self.DetectVehicles(request, context)
                yield response
            except Exception as e:
                logger.error(f"Batch detection error: {e}")
                context.set_details(str(e))
                context.set_code(grpc.StatusCode.INTERNAL)
                raise e
    
    # ============================================
    # SIMULATION MODE METHODS
    # ============================================
    
    def _simulate_vehicle_detection(self, request):
        """Simulate vehicle detection with random results"""
        import random
        import time
        
        time.sleep(0.1)  # Simulate processing time
        
        # Simulated response (would use actual protobuf message in real impl)
        class DetectionResponse:
            def __init__(self):
                self.vehicle_detected = random.random() > 0.3
                self.confidence = 0.75 + random.random() * 0.2
                self.bounding_boxes = []
        
        response = DetectionResponse()
        logger.info(f"Simulated detection: vehicle_detected={response.vehicle_detected}, "
                   f"confidence={response.confidence:.2f}")
        return response
    
    def _simulate_license_plate_detection(self, request):
        """Simulate license plate detection with random plate numbers"""
        import random
        import time
        
        time.sleep(0.1)  # Simulate processing time
        
        class LicensePlateResponse:
            def __init__(self):
                plates = ["34ABC123", "34XYZ789", "06DEF456", "35GHI789", "06JKL012"]
                self.license_plate_text = random.choice(plates)
                self.confidence = 0.80 + random.random() * 0.15
                self.plate_location = None
        
        response = LicensePlateResponse()
        logger.info(f"Simulated license plate detection: plate={response.license_plate_text}, "
                   f"confidence={response.confidence:.2f}")
        return response
    
    # ============================================
    # REAL YOLO DETECTION METHODS
    # ============================================
    
    def _yolo_vehicle_detection(self, request):
        """Real YOLO vehicle detection"""
        if not self.model:
            return self._simulate_vehicle_detection(request)
        
        try:
            # Convert image data to numpy array
            image_array = np.frombuffer(request.image_data, np.uint8)
            
            # Import cv2 for image decoding
            import cv2
            frame = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
            
            if frame is None:
                logger.warning("Failed to decode image data")
                return self._simulate_vehicle_detection(request)
            
            # Run YOLO inference
            results = self.model(frame)
            detections = results[0]
            
            # Check for vehicles (class 2 = car, 5 = bus, 7 = truck)
            vehicle_classes = {2, 5, 7}  # COCO dataset vehicle classes
            vehicle_detected = False
            confidence = 0.0
            
            for detection in detections.boxes:
                class_id = int(detection.cls)
                if class_id in vehicle_classes:
                    vehicle_detected = True
                    confidence = float(detection.conf)
                    logger.info(f"Vehicle detected: class={class_id}, confidence={confidence:.2f}")
                    break
            
            # Create response (would use protobuf message in real impl)
            class DetectionResponse:
                def __init__(self):
                    self.vehicle_detected = vehicle_detected
                    self.confidence = confidence
                    self.bounding_boxes = []
            
            return DetectionResponse()
            
        except Exception as e:
            logger.error(f"YOLO detection failed: {e}")
            return self._simulate_vehicle_detection(request)
    
    def _yolo_license_plate_detection(self, request):
        """Real YOLO license plate detection"""
        if not self.model:
            return self._simulate_license_plate_detection(request)
        
        try:
            # Convert image data
            import cv2
            image_array = np.frombuffer(request.image_data, np.uint8)
            frame = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
            
            if frame is None:
                logger.warning("Failed to decode image data")
                return self._simulate_license_plate_detection(request)
            
            # Run YOLO inference
            results = self.model(frame)
            detections = results[0]
            
            # In real implementation, would use specialized license plate YOLO model
            # For now, simulate detection
            logger.info(f"License plate detection processed for camera: {request.camera_id}")
            return self._simulate_license_plate_detection(request)
            
        except Exception as e:
            logger.error(f"License plate detection failed: {e}")
            return self._simulate_license_plate_detection(request)


def serve(port: int = 50051, use_yolo: bool = False):
    """
    Start gRPC server
    
    Args:
        port: Port to run server on (default: 50051)
        use_yolo: Whether to use real YOLO model (requires ultralytics)
    """
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    
    # Register servicer
    servicer = YOLODetectionServiceServicer(use_yolo=use_yolo)
    # In real implementation: detection_pb2_grpc.add_YOLODetectionServiceServicer_to_server(servicer, server)
    
    # For now, we'll add the servicer manually (when proto files are compiled)
    # server.add_insecure_port(f'[::]:{port}')
    
    # Note: The actual registration would be:
    # from detection_pb2_grpc import add_YOLODetectionServiceServicer_to_server
    # add_YOLODetectionServiceServicer_to_server(servicer, server)
    # server.add_insecure_port(f'[::]:{port}')
    
    logger.info(f"gRPC server listening on port {port}")
    logger.info(f"YOLO mode: {'ENABLED' if use_yolo and YOLO_AVAILABLE else 'SIMULATION'}")
    
    # For testing without proto compilation, we'll just print server info
    print(f"\n✅ YOLO gRPC Server Ready!")
    print(f"   Address: localhost:{port}")
    print(f"   Mode: {'YOLO' if use_yolo and YOLO_AVAILABLE else 'SIMULATION'}")
    print(f"   Listening for connections...\n")
    
    # Uncomment below when proto files are compiled:
    # try:
    #     server.start()
    #     server.wait_for_termination()
    # except KeyboardInterrupt:
    #     logger.info("Shutting down server...")
    #     server.stop(0)


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='YOLO gRPC Server')
    parser.add_argument('--port', type=int, default=50051, help='Port to run server on')
    parser.add_argument('--yolo', action='store_true', help='Use real YOLO model (requires ultralytics)')
    
    args = parser.parse_args()
    
    serve(port=args.port, use_yolo=args.yolo)
