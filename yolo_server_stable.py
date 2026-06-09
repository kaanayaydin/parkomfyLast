#!/usr/bin/env python3
"""
Stable YOLO Server - Minimal dependencies, crash-resistant
Safe for testing without GPU/CUDA issues
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import random
import logging
import threading
import time
from urllib.parse import urlparse
import sys
import traceback

# Configure logging with file output for debugging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('yolo_server.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class YOLODetectionHandler(BaseHTTPRequestHandler):
    """Stable HTTP handler for YOLO detection requests"""
    
    def log_message(self, format, *args):
        """Override to use logger instead of stderr"""
        logger.info(format % args)
    
    def do_GET(self):
        """Handle GET requests safely"""
        try:
            parsed_path = urlparse(self.path)
            
            if parsed_path.path == '/health':
                self._send_json_response(200, {
                    'status': 'UP',
                    'service': 'YOLO Detection Server',
                    'mode': 'SIMULATION',
                    'timestamp': time.time()
                })
                logger.info("Health check passed")
                
            elif parsed_path.path == '/detect':
                # Simulate vehicle detection
                response = self._generate_detection()
                self._send_json_response(200, response)
                logger.info(f"Detection response: {response}")
                
            elif parsed_path.path == '/status':
                self._send_json_response(200, {
                    'status': 'READY',
                    'server': 'YOLO-Stable',
                    'memory_safe': True,
                    'gpu_required': False
                })
                
            else:
                self._send_json_response(404, {
                    'error': f'Endpoint {parsed_path.path} not found'
                })
                
        except Exception as e:
            logger.error(f"Error handling GET request: {e}")
            logger.error(traceback.format_exc())
            self._send_json_response(500, {
                'error': str(e),
                'type': type(e).__name__
            })
    
    def do_POST(self):
        """Handle POST requests safely"""
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            
            parsed_path = urlparse(self.path)
            
            if parsed_path.path == '/detect':
                # Parse incoming detection request
                request_data = json.loads(body) if body else {}
                response = self._generate_detection(request_data.get('camera_id'))
                self._send_json_response(200, response)
                logger.info(f"POST Detection: camera_id={request_data.get('camera_id')}")
                
            else:
                self._send_json_response(404, {'error': 'Endpoint not found'})
                
        except json.JSONDecodeError:
            logger.error("Invalid JSON in request")
            self._send_json_response(400, {'error': 'Invalid JSON'})
        except Exception as e:
            logger.error(f"Error handling POST request: {e}")
            logger.error(traceback.format_exc())
            self._send_json_response(500, {'error': str(e)})
    
    def _generate_detection(self, camera_id: str = None) -> dict:
        """Generate simulated detection response (memory safe)"""
        has_vehicle = random.random() > 0.4
        num_vehicles = random.randint(0, 3) if has_vehicle else 0
        
        vehicles = []
        for i in range(num_vehicles):
            vehicles.append({
                'id': f'vehicle_{i}',
                'confidence': round(0.75 + random.random() * 0.24, 3),
                'bbox': {
                    'x': random.randint(50, 900),
                    'y': random.randint(50, 700),
                    'width': random.randint(50, 200),
                    'height': random.randint(50, 150)
                },
                'class': random.choice(['car', 'motorcycle', 'truck']),
                'license_plate': f'ABC{random.randint(1000, 9999)}' if random.random() > 0.3 else None
            })
        
        return {
            'camera_id': camera_id or 'default',
            'timestamp': time.time(),
            'detection_count': len(vehicles),
            'vehicles': vehicles,
            'inference_time_ms': round(random.uniform(10, 50), 2)
        }
    
    def _send_json_response(self, status_code: int, data: dict):
        """Send JSON response safely"""
        try:
            self.send_response(status_code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            response_body = json.dumps(data).encode('utf-8')
            self.wfile.write(response_body)
        except Exception as e:
            logger.error(f"Error sending response: {e}")


def start_server(host: str = '127.0.0.1', port: int = 50051):
    """Start YOLO detection server with error handling"""
    try:
        server_address = (host, port)
        httpd = HTTPServer(server_address, YOLODetectionHandler)
        logger.info(f"YOLO Detection Server started on {host}:{port}")
        logger.info("Endpoints available:")
        logger.info(f"  - GET  http://{host}:{port}/health")
        logger.info(f"  - GET  http://{host}:{port}/detect")
        logger.info(f"  - GET  http://{host}:{port}/status")
        logger.info(f"  - POST http://{host}:{port}/detect")
        logger.info("Press Ctrl+C to stop server")
        
        httpd.serve_forever()
        
    except OSError as e:
        logger.error(f"Failed to start server: {e}")
        logger.info("Port may be in use. Try: netstat -ano | findstr :50051")
        sys.exit(1)
    except KeyboardInterrupt:
        logger.info("Server shutting down...")
        httpd.shutdown()
        logger.info("Server stopped")
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        logger.error(traceback.format_exc())
        sys.exit(1)


if __name__ == '__main__':
    # Parse command line arguments
    host = '127.0.0.1'
    port = 50051
    
    for i, arg in enumerate(sys.argv[1:], 1):
        if arg == '--host' and i < len(sys.argv):
            host = sys.argv[i + 1]
        elif arg == '--port' and i < len(sys.argv):
            try:
                port = int(sys.argv[i + 1])
            except ValueError:
                logger.error(f"Invalid port: {sys.argv[i + 1]}")
                sys.exit(1)
    
    logger.info("=" * 60)
    logger.info("YOLO Detection Server (Stable)")
    logger.info("=" * 60)
    
    start_server(host, port)
