/**
 * Spring Boot REST API (Java backend). Not Flask (5000/5001).
 */
export const API_BASE = 'http://localhost:8080/api/v1';

export const AREA_IDS = {
  istasyon1: 'AREA-001',
  istasyon2: 'AREA-002',
  istasyon3: 'AREA-003',
};

export async function getParkingStatus(areaId) {
  const res = await fetch(`${API_BASE}/parking/status?areaId=${encodeURIComponent(areaId)}`);
  return res.json();
}

export async function createParkingSession({ areaId, licensePlate, vehicleType = 'CAR' }) {
  const res = await fetch(`${API_BASE}/parking/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ areaId, licensePlate, vehicleType }),
  });
  return res.json();
}

export async function exitParkingSession(sessionId) {
  const res = await fetch(`${API_BASE}/parking/sessions/${sessionId}/exit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  });
  return res.json();
}

export async function getAvailableSlots(areaId) {
  const res = await fetch(`${API_BASE}/parking/slots/available?areaId=${encodeURIComponent(areaId)}`);
  return res.json();
}

export async function healthCheck() {
  const res = await fetch(`${API_BASE}/health`);
  return res.json();
}
