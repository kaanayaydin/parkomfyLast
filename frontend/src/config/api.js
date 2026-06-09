import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Spring Boot REST API (Java backend). Not Flask (5000/5001).
 * Expo Go on a physical device cannot reach "localhost" (that is the phone itself).
 * Use the dev machine IP from Expo's debugger host, or set API_HOST_OVERRIDE below.
 */
// Sabit IP yazmayın; Wi-Fi değişince telefon sunucuya ulaşamaz (ERR_ADDRESS_UNREACHABLE).
// Gerekirse geçici olarak buraya yazın, yoksa Expo'nun debugger host'unu kullanır.
const API_HOST_OVERRIDE = null;

function resolveApiHost() {
  if (API_HOST_OVERRIDE) return API_HOST_OVERRIDE;
  if (Platform.OS === 'web') return 'localhost';

  const debuggerHost =
    Constants.expoConfig?.hostUri ??
    Constants.expoGoConfig?.debuggerHost ??
    Constants.manifest2?.extra?.expoGo?.debuggerHost ??
    Constants.manifest?.debuggerHost;

  if (debuggerHost) {
    return debuggerHost.split(':')[0];
  }

  return 'localhost';
}

export const API_HOST = resolveApiHost();
export const API_BASE = `http://${API_HOST}:8080/api/v1`;
export const WS_URL = `ws://${API_HOST}:8080/ws/parking`;

let authToken = null;

export function setAuthToken(token) {
  authToken = token || null;
}

function authHeaders(extra = {}) {
  const headers = { ...extra };
  if (authToken) {
    headers['X-Auth-Token'] = authToken;
  }
  return headers;
}

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

export async function getAvailableSlots(areaId, startTime, endTime) {
  let url = `${API_BASE}/parking/slots/available?areaId=${encodeURIComponent(areaId)}`;
  if (startTime && endTime) {
    url += `&startTime=${encodeURIComponent(startTime)}&endTime=${encodeURIComponent(endTime)}`;
  }
  const res = await fetch(url);
  return res.json();
}

export async function createReservation({ areaId, slotId, licensePlate, startTime, endTime }) {
  const res = await fetch(`${API_BASE}/parking/reservations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ areaId, slotId, licensePlate, startTime, endTime }),
  });
  return res.json();
}

export async function getReservations(licensePlate) {
  const res = await fetch(
    `${API_BASE}/parking/reservations?licensePlate=${encodeURIComponent(licensePlate)}`
  );
  return res.json();
}

export async function cancelReservation(reservationId, licensePlate) {
  const res = await fetch(
    `${API_BASE}/parking/reservations/${encodeURIComponent(reservationId)}/cancel?licensePlate=${encodeURIComponent(licensePlate)}`,
    { method: 'POST' }
  );
  return res.json();
}

export function reservationStatusLabel(status) {
  switch (status) {
    case 'RESERVED': return 'Aktif';
    case 'ACTIVE': return 'Devam ediyor';
    case 'COMPLETED': return 'Tamamlandı';
    case 'CANCELLED': return 'İptal edildi';
    default: return status || '-';
  }
}

export function canCancelReservation(item) {
  return item?.status === 'RESERVED' && item?.reservationId;
}

/** YYYY-MM-DDTHH:mm:00 for backend from a full Date */
export function buildDateTimeFromDate(d) {
  const date = new Date(d);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const h = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${day}T${h}:${min}:00`;
}

/** dateOrOffset: Date (uses its clock) or day offset number + hour */
export function buildDateTime(dateOrOffset, hour) {
  if (dateOrOffset instanceof Date) {
    return buildDateTimeFromDate(dateOrOffset);
  }
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + dateOrOffset);
  d.setHours(hour, 0, 0, 0);
  return buildDateTimeFromDate(d);
}

export function applyDatePart(base, newDate) {
  const d = new Date(base);
  d.setFullYear(newDate.getFullYear(), newDate.getMonth(), newDate.getDate());
  return d;
}

export function applyTimePart(base, newTime) {
  const d = new Date(base);
  d.setHours(newTime.getHours(), newTime.getMinutes(), 0, 0);
  return d;
}

export function formatTimeLabel(date) {
  if (!date) return '';
  return date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
}

export function formatDateLabel(date) {
  if (!date) return '';
  return date.toLocaleDateString('tr-TR', {
    weekday: 'short',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

export function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

export function formatDateTime(iso) {
  if (!iso) return '';
  const d = new Date(iso.length === 19 ? iso : iso.substring(0, 19));
  return d.toLocaleString('tr-TR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export async function healthCheck() {
  const res = await fetch(`${API_BASE}/health`);
  return res.json();
}

export async function registerUser({ email, password, fullName, licensePlate }) {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, fullName, licensePlate }),
  });
  return res.json();
}

export async function loginUser(email, password) {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const json = await res.json();
  if (json.success && json.data?.token) {
    setAuthToken(json.data.token);
  }
  return json;
}

export async function getKvkkText() {
  const res = await fetch(`${API_BASE}/legal/kvkk`);
  return res.json();
}

export function mapUserFromApi(data) {
  return {
    userId: data.userId,
    email: data.email,
    fullName: data.fullName,
    plate: data.licensePlate,
    role: data.role,
  };
}

export async function getLiveParkingStatus(areaId, startTime, endTime) {
  let url = `${API_BASE}/parking/live?areaId=${encodeURIComponent(areaId)}`;
  if (startTime) url += `&startTime=${encodeURIComponent(startTime)}`;
  if (endTime) url += `&endTime=${encodeURIComponent(endTime)}`;
  const res = await fetch(url);
  return res.json();
}

export function getLiveImageUrl(areaId) {
  return `${API_BASE}/parking/live-image?areaId=${encodeURIComponent(areaId)}&t=${Date.now()}`;
}

export async function processEntrancePlate(imageUri) {
  const form = new FormData();
  form.append('image', {
    uri: imageUri,
    name: 'entrance.jpg',
    type: 'image/jpeg',
  });
  const res = await fetch(`${API_BASE}/entry/plate`, { method: 'POST', body: form });
  return res.json();
}

export async function processParkingCameraScan(imageUri, areaId) {
  const form = new FormData();
  form.append('image', {
    uri: imageUri,
    name: 'parking.jpg',
    type: 'image/jpeg',
  });
  const res = await fetch(
    `${API_BASE}/parking/camera/scan?areaId=${encodeURIComponent(areaId)}`,
    { method: 'POST', body: form }
  );
  return res.json();
}

export async function detectPlateFromImage(imageUri) {
  const form = new FormData();
  form.append('image', {
    uri: imageUri,
    name: 'plate.jpg',
    type: 'image/jpeg',
  });
  const res = await fetch(`${API_BASE}/detect/plate`, { method: 'POST', body: form });
  return res.json();
}

export async function registerPushToken(userId, expoPushToken) {
  const res = await fetch(`${API_BASE}/notifications/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, expoPushToken }),
  });
  return res.json();
}

export async function getAdminReservations(areaId) {
  let url = `${API_BASE}/admin/reservations`;
  if (areaId) url += `?areaId=${encodeURIComponent(areaId)}`;
  const res = await fetch(url, { headers: authHeaders() });
  return res.json();
}

export async function getAdminSessions(areaId) {
  const res = await fetch(
    `${API_BASE}/admin/sessions?areaId=${encodeURIComponent(areaId)}`,
    { headers: authHeaders() }
  );
  return res.json();
}

export async function getAdminDetections(areaId) {
  const res = await fetch(
    `${API_BASE}/admin/detections?areaId=${encodeURIComponent(areaId)}`,
    { headers: authHeaders() }
  );
  return res.json();
}

export function connectParkingWebSocket(onMessage) {
  let ws;
  try {
    ws = new WebSocket(WS_URL);
    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        onMessage(payload);
      } catch {
        /* ignore */
      }
    };
    ws.onerror = () => {};
  } catch {
    return () => {};
  }
  return () => {
    if (ws && ws.readyState === WebSocket.OPEN) ws.close();
  };
}

export async function listParkingAreas() {
  const res = await fetch(`${API_BASE}/admin/parking-areas`, { headers: authHeaders() });
  return res.json();
}

/** Sürücü: kalibre edilmiş otopark listesi */
export async function getPublicParkingAreas() {
  const res = await fetch(`${API_BASE}/parking/areas`);
  return res.json();
}

export async function createParkingArea({ areaName, address, lotKey }) {
  const res = await fetch(`${API_BASE}/admin/parking-areas`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ areaName, address, lotKey }),
  });
  return res.json();
}

export function getLiveCameraSnapshotUrl(lotKey = 'loop1', cacheBust = Date.now()) {
  const lot = String(lotKey || 'loop1').replace('.mp4', '');
  return `${API_BASE}/camera/live/snapshot?lot=${encodeURIComponent(lot)}&t=${cacheBust}`;
}

export async function getLiveCameraStatus() {
  const res = await fetch(`${API_BASE}/camera/live/status`);
  return res.json();
}

export async function predictSlotsFromLiveCamera() {
  const res = await fetch(`${API_BASE}/camera/live/predict-slots`, { method: 'POST' });
  return res.json();
}

export async function scanLiveCamera(areaId) {
  const res = await fetch(
    `${API_BASE}/camera/live/scan?areaId=${encodeURIComponent(areaId)}`,
    { method: 'POST' }
  );
  return res.json();
}

export async function predictSlotLayout(imageUri) {
  const form = new FormData();
  form.append('image', { uri: imageUri, name: 'layout.jpg', type: 'image/jpeg' });
  const res = await fetch(`${API_BASE}/admin/parking-areas/predict-slots`, {
    method: 'POST',
    headers: authHeaders(),
    body: form,
  });
  return res.json();
}

export async function saveSlotCalibration(payload) {
  const res = await fetch(`${API_BASE}/admin/parking-areas/calibrate`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(payload),
  });
  return res.json();
}

export function mergedStatusColor(status) {
  switch (status) {
    case 'AVAILABLE': return '#4CAF50';
    case 'OCCUPIED': return '#F44336';
    case 'RESERVED': return '#FF9800';
    case 'MAINTENANCE': return '#9E9E9E';
    default: return '#BDBDBD';
  }
}
