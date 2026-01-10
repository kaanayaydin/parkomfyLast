#!/usr/bin/env python3
"""
Simple YOLO Simulation Server (No gRPC dependencies required for testing)
Provides vehicle detection data via basic HTTP endpoint
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import random
import logging
from urllib.parse import urlparse, parse_qs
import sys

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class YOLOHandler(BaseHTTPRequestHandler):
    """Simple HTTP handler for YOLO detection requests"""
    
    def do_GET(self):
        """Handle GET requests"""
        parsed_path = urlparse(self.path)
        
        if parsed_path.path == '/health':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            response = {
                'status': 'UP',
                'service': 'YOLO Server',
                'mode': 'SIMULATION'
            }
            self.wfile.write(json.dumps(response).encode())
            logger.info("Health check: OK")
            
        elif parsed_path.path == '/detect':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            # Simulated detection
            vehicle_detected = random.random() > 0.3
            confidence = 0.75 + random.random() * 0.2
            
            response = {
                'vehicle_detected': vehicle_detected,
                'confidence': round(confidence, 3),
                'bounding_boxes': []
            }
            
            self.wfile.write(json.dumps(response).encode())
            logger.info(f"Vehicle detection: detected={vehicle_detected}, confidence={confidence:.2f}")
            
        elif parsed_path.path == '/detect_plate':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            # Simulated license plate detection
            plates = ["34ABC123", "34XYZ789", "06DEF456", "35GHI789", "06JKL012"]
            plate = random.choice(plates)
            confidence = 0.80 + random.random() * 0.15
            
            response = {
                'license_plate_text': plate,
                'confidence': round(confidence, 3),
                'plate_location': None
            }
            
            self.wfile.write(json.dumps(response).encode())
            logger.info(f"License plate detection: plate={plate}, confidence={confidence:.2f}")
            
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found')
    
    def log_message(self, format, *args):
        """Suppress default logging"""
        pass


def start_server(port=50052):
    """Start simple HTTP server"""
    server_address = ('', port)
    httpd = HTTPServer(server_address, YOLOHandler)
    
    print(f"\n✅ YOLO Detection Server Ready!")
    print(f"   Address: http://localhost:{port}")
    print(f"   Mode: SIMULATION (No ML model loaded)")
    print(f"   Endpoints:")
    print(f"     - GET /health - Server health check")
    print(f"     - GET /detect - Vehicle detection")
    print(f"     - GET /detect_plate - License plate detection")
    print(f"   Listening for connections...\n")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down server...")
        httpd.server_close()
        print("\n👋 Server stopped")


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='YOLO Detection Server (Simulation Mode)')
    parser.add_argument('--port', type=int, default=50052, help='Port to run server on')
    
    args = parser.parse_args()
    
    start_server(port=args.port)
