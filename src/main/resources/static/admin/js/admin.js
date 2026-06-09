const API = `${window.location.origin}/api/v1`;
const SNAPSHOT_URL = `${API}/camera/live/snapshot`;
let snapshotTimer = null;

const state = {
  currentVideo: 'loop1',
  view: 'list',
  step: 1,
  areas: [],
  selectedArea: null,
  setup: {
    areaId: null,
    lotKey: '',
    imageBase64: null,
    imageWidth: 1280,
    imageHeight: 720,
    slots: [],
    selectedSlot: 0,
  },
  drag: null,
};

function toast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.style.display = 'block';
  setTimeout(() => { el.style.display = 'none'; }, 3000);
}

async function api(path, opts = {}) {
  const res = await fetch(`${API}${path}`, opts);
  return res.json();
}

function showView(name) {
  state.view = name;
  document.querySelectorAll('[data-view]').forEach((el) => {
    el.classList.toggle('hidden', el.dataset.view !== name);
  });
  if (name === 'setup' && state.step === 2) startLiveVideo('setupLiveStream');
  if (name === 'detail') startLiveVideo('liveStream');
}

function startLiveVideo(elementId) {
  const el = document.getElementById(elementId);
  if (!el) return;
  const tick = () => {
    el.src = `${SNAPSHOT_URL}?t=${Date.now()}`;
  };
  tick();
  if (snapshotTimer) clearInterval(snapshotTimer);
  snapshotTimer = setInterval(tick, 90);
}

function stopLiveVideo() {
  if (snapshotTimer) {
    clearInterval(snapshotTimer);
    snapshotTimer = null;
  }
}

async function loadVideoOptions() {
  try {
    const res = await fetch(`${API}/camera/live/videos`);
    const data = await res.json();
    const sel = document.getElementById('videoSelect');
    if (!sel) return;
    const videos = data.videos || [];
    const current = (data.current || 'loop1.mp4').replace('.mp4', '');
    state.currentVideo = current;
    sel.innerHTML = videos.map((v) =>
      `<option value="${v.id}" ${v.id === current ? 'selected' : ''}>${v.label} (${v.name})</option>`
    ).join('');
  } catch {
    const sel = document.getElementById('videoSelect');
    if (sel) {
      sel.innerHTML = '<option value="loop1">Loop 1</option><option value="loop2">Loop 2</option><option value="loop3">Loop 3</option>';
    }
  }
}

async function onVideoChange(videoId) {
  if (!videoId) return;
  const res = await fetch(`${API}/camera/live/video?video=${encodeURIComponent(videoId)}`, { method: 'POST' });
  const data = await res.json();
  if (data.success) {
    state.currentVideo = videoId;
    toast(`Kamera: ${data.current || videoId}`);
    startLiveVideo('liveStream');
    startLiveVideo('setupLiveStream');
  } else {
    toast(data.message || 'Video değiştirilemedi');
    loadVideoOptions();
  }
}

// ─── Liste ───
async function loadAreas() {
  const res = await api('/admin/parking-areas');
  state.areas = res.success ? res.data : [];
  const list = document.getElementById('areaList');
  if (!state.areas.length) {
    list.innerHTML = '<p class="hint">Henüz otopark yok. Yeni kayıt oluşturun.</p>';
    return;
  }
  list.innerHTML = state.areas.map((a) => `
    <div class="area-item">
      <div>
        <strong>${a.areaName}</strong> <span class="badge ${a.calibrated ? 'ok' : 'warn'}">${a.calibrated ? 'Kalibre' : 'Kalibre değil'}</span>
        <div class="hint">${a.areaId} · ${a.slotCount} slot · ${a.address || ''}</div>
      </div>
      <div class="row">
        ${!a.calibrated ? `<button class="btn blue" onclick="startSetupFor('${a.areaId}')">Kalibre Et</button>` : ''}
        <button class="btn" onclick="openDetail('${a.areaId}')">Canlı İzle</button>
      </div>
    </div>
  `).join('');
}

function startSetupFor(areaId) {
  const area = state.areas.find((a) => a.areaId === areaId);
  if (!area) return;
  state.setup = { areaId, lotKey: area.lotKey || '', imageBase64: null, imageWidth: 1280, imageHeight: 720, slots: [], selectedSlot: 0 };
  state.step = 2;
  showView('setup');
  renderSetup();
}

function startNewSetup() {
  state.setup = { areaId: null, lotKey: '', imageBase64: null, imageWidth: 1280, imageHeight: 720, slots: [], selectedSlot: 0 };
  state.step = 1;
  showView('setup');
  renderSetup();
}

// ─── Kurulum ───
function renderSetup() {
  document.getElementById('setupStep1').classList.toggle('hidden', state.step !== 1);
  document.getElementById('setupStep2').classList.toggle('hidden', state.step !== 2);
  document.getElementById('setupStep3').classList.toggle('hidden', state.step !== 3);
  document.getElementById('setupStepLabel').textContent = `Adım ${state.step}/3`;
  if (state.step === 2) startLiveVideo('setupLiveStream');
  if (state.step === 3) drawCalibration();
  renderSlotChips();
}

async function createArea() {
  const name = document.getElementById('areaName').value.trim();
  const address = document.getElementById('areaAddress').value.trim();
  const lotKey = document.getElementById('lotKey').value.trim();
  if (!name) { toast('Otopark adı gerekli'); return; }
  const res = await api('/admin/parking-areas', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ areaName: name, address, lotKey: lotKey || undefined }),
  });
  if (!res.success) { toast(res.message || 'Hata'); return; }
  state.setup.areaId = res.data.areaId;
  state.setup.lotKey = res.data.lotKey;
  state.step = 2;
  toast('Otopark oluşturuldu');
  renderSetup();
  startLiveVideo('setupLiveStream');
}

async function predictFromLive() {
  toast('Model işliyor…');
  const res = await api('/camera/live/predict-slots', { method: 'POST' });
  if (!res.success) { toast(res.message || 'Tahmin başarısız'); return; }
  applyPrediction(res.data);
  state.step = 3;
  renderSetup();
  toast(`${state.setup.slots.length} slot tahmini alındı`);
}

function applyPrediction(data) {
  const slots = data.slots || [];
  state.setup.imageWidth = data.imageWidth || 1280;
  state.setup.imageHeight = data.imageHeight || 720;
  state.setup.imageBase64 = data.imageBase64 || null;
  state.setup.slots = slots.map((s, i) => ({
    slotNumber: s.slotNumber || i + 1,
    corners: (s.corners || []).map((c) => ({ x: c.x, y: c.y })),
    occupied: !!s.occupied,
    manual: false,
  }));
  state.setup.selectedSlot = 0;
}

function renumberSlots() {
  state.setup.slots.forEach((s, i) => { s.slotNumber = i + 1; });
}

function addSlot() {
  const n = state.setup.slots.length;
  const cols = 3;
  const row = Math.floor(n / cols);
  const col = n % cols;
  const margin = 0.06;
  const cellW = (1 - 2 * margin) / cols;
  const cellH = 0.18;
  const x0 = margin + col * cellW;
  const y0 = Math.min(0.72, 0.42 + row * cellH);
  const inset = 0.015;
  state.setup.slots.push({
    slotNumber: n + 1,
    occupied: false,
    manual: true,
    corners: [
      { x: x0 + inset, y: y0 + inset },
      { x: x0 + cellW - inset, y: y0 + inset },
      { x: x0 + cellW - inset, y: y0 + cellH - inset },
      { x: x0 + inset, y: y0 + cellH - inset },
    ],
  });
  state.setup.selectedSlot = state.setup.slots.length - 1;
  drawCalibration();
  renderSlotChips();
}

function deleteSlot() {
  const idx = state.setup.selectedSlot;
  if (!state.setup.slots.length) return;
  if (!confirm(`Slot #${state.setup.slots[idx].slotNumber} silinsin mi?`)) return;
  state.setup.slots.splice(idx, 1);
  renumberSlots();
  state.setup.selectedSlot = Math.min(idx, state.setup.slots.length - 1);
  drawCalibration();
  renderSlotChips();
}

function renderSlotChips() {
  const el = document.getElementById('slotChips');
  if (!el) return;
  el.innerHTML = state.setup.slots.map((s, i) =>
    `<span class="chip ${i === state.setup.selectedSlot ? 'active' : ''}" onclick="selectSlot(${i})">#${s.slotNumber}</span>`
  ).join('');
  const slot = state.setup.slots[state.setup.selectedSlot];
  const coords = document.getElementById('cornerCoords');
  if (coords && slot) {
    const labels = ['Sol-Üst', 'Sağ-Üst', 'Sağ-Alt', 'Sol-Alt'];
    coords.innerHTML = '<p class="hint">Köşe noktalarını fare ile sürükleyin</p>' +
      slot.corners.map((c, i) => `<div class="corner-coords">${labels[i]}: ${c.x.toFixed(3)}, ${c.y.toFixed(3)}</div>`).join('');
  }
}

function selectSlot(i) {
  state.setup.selectedSlot = i;
  drawCalibration();
  renderSlotChips();
}

async function saveCalibration() {
  if (!state.setup.areaId || !state.setup.slots.length) {
    toast('En az bir slot gerekli');
    return;
  }
  const res = await api('/admin/parking-areas/calibrate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      areaId: state.setup.areaId,
      lotKey: state.setup.lotKey,
      imageWidth: state.setup.imageWidth,
      imageHeight: state.setup.imageHeight,
      slots: state.setup.slots.map((s) => ({
        slotNumber: s.slotNumber,
        corners: s.corners,
        occupied: !!s.occupied,
      })),
    }),
  });
  if (!res.success) { toast(res.message || 'Kayıt hatası'); return; }
  toast(`${res.data.slotCount} slot kaydedildi`);
  await loadAreas();
  showView('list');
}

// ─── Kalibrasyon canvas ───
let calibImg = null;

function drawCalibration() {
  const canvas = document.getElementById('calibCanvas');
  const wrap = document.getElementById('calibWrap');
  if (!canvas || !state.setup.imageBase64) return;

  if (!calibImg) {
    calibImg = new Image();
    calibImg.onload = () => drawCalibration();
    calibImg.src = `data:image/jpeg;base64,${state.setup.imageBase64}`;
    return;
  }

  const maxW = wrap.clientWidth || 800;
  const scale = maxW / calibImg.width;
  canvas.width = maxW;
  canvas.height = calibImg.height * scale;
  const ctx = canvas.getContext('2d');
  ctx.drawImage(calibImg, 0, 0, canvas.width, canvas.height);

  state.setup.slots.forEach((slot, idx) => {
    if (!slot.corners || slot.corners.length < 4) return;
    const isSel = idx === state.setup.selectedSlot;
    const color = isSel ? '#ffeb3b' : (slot.manual ? '#2196f3' : (slot.occupied ? '#f44336' : '#4caf50'));
    const pts = slot.corners.map((c) => ({ x: c.x * canvas.width, y: c.y * canvas.height }));
    ctx.strokeStyle = color;
    ctx.lineWidth = isSel ? 3 : 2;
    ctx.beginPath();
    pts.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
    ctx.closePath();
    ctx.stroke();
    ctx.fillStyle = color;
    pts.forEach((p) => {
      ctx.beginPath();
      ctx.arc(p.x, p.y, isSel ? 10 : 6, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2;
      ctx.stroke();
    });
    const cx = pts.reduce((s, p) => s + p.x, 0) / 4;
    const cy = pts.reduce((s, p) => s + p.y, 0) / 4;
    ctx.fillStyle = '#000';
    ctx.font = 'bold 14px sans-serif';
    ctx.fillText(`#${slot.slotNumber}`, cx - 8, cy + 4);
  });
}

function canvasPos(e) {
  const canvas = document.getElementById('calibCanvas');
  const rect = canvas.getBoundingClientRect();
  return { x: e.clientX - rect.left, y: e.clientY - rect.top, w: canvas.width, h: canvas.height };
}

function hitCorner(x, y) {
  const slot = state.setup.slots[state.setup.selectedSlot];
  if (!slot) return null;
  const canvas = document.getElementById('calibCanvas');
  for (let i = 0; i < 4; i++) {
    const px = slot.corners[i].x * canvas.width;
    const py = slot.corners[i].y * canvas.height;
    if (Math.hypot(x - px, y - py) < 16) return i;
  }
  return null;
}

function initCanvasEvents() {
  const canvas = document.getElementById('calibCanvas');
  canvas.addEventListener('mousedown', (e) => {
    const p = canvasPos(e);
    const ci = hitCorner(p.x, p.y);
    if (ci !== null) state.drag = { cornerIdx: ci };
  });
  canvas.addEventListener('mousemove', (e) => {
    if (!state.drag) return;
    const p = canvasPos(e);
    const slot = state.setup.slots[state.setup.selectedSlot];
    slot.corners[state.drag.cornerIdx] = {
      x: Math.max(0, Math.min(1, Math.round((p.x / p.w) * 1000) / 1000)),
      y: Math.max(0, Math.min(1, Math.round((p.y / p.h) * 1000) / 1000)),
    };
    drawCalibration();
    renderSlotChips();
  });
  window.addEventListener('mouseup', () => { state.drag = null; });
}

// ─── Canlı izleme ───
let detailTimer = null;
let liveSlotsCache = [];

function slotOverlayColor(merged) {
  if (merged === 'OCCUPIED') return { stroke: '#e53935', fill: 'rgba(229,57,53,0.35)' };
  if (merged === 'RESERVED') return { stroke: '#fb8c00', fill: 'rgba(251,140,0,0.32)' };
  if (merged === 'MAINTENANCE') return { stroke: '#9e9e9e', fill: 'rgba(158,158,158,0.3)' };
  return { stroke: '#43a047', fill: 'rgba(67,160,71,0.28)' };
}

function drawLiveOverlay(slots) {
  const wrap = document.getElementById('liveVideoWrap');
  const canvas = document.getElementById('liveOverlay');
  if (!wrap || !canvas || !wrap.clientWidth) return;

  const w = wrap.clientWidth;
  const h = wrap.clientHeight;
  canvas.width = w;
  canvas.height = h;

  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, w, h);

  (slots || []).forEach((slot) => {
    const corners = slot.corners;
    if (!corners || corners.length < 4) return;

    const { stroke, fill } = slotOverlayColor(slot.mergedStatus);
    const pts = corners.map((c) => ({ x: (c.x || 0) * w, y: (c.y || 0) * h }));

    ctx.beginPath();
    pts.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
    ctx.closePath();
    ctx.fillStyle = fill;
    ctx.fill();
    ctx.strokeStyle = stroke;
    ctx.lineWidth = 2;
    ctx.stroke();

    const cx = pts.reduce((s, p) => s + p.x, 0) / 4;
    const cy = pts.reduce((s, p) => s + p.y, 0) / 4;
    const label = slot.displayLabel || (slot.mergedStatus === 'AVAILABLE' ? 'BOŞ' : slot.mergedStatus);
    const lines = [`#${slot.slotNumber}`, label];
    if (slot.licensePlate) {
      lines.push(`🚗 ${slot.licensePlate}`);
    } else if (slot.mergedStatus === 'AVAILABLE') {
      lines.push('Plaka yok');
    }

    ctx.font = 'bold 13px Segoe UI, sans-serif';
    const lineH = 16;
    const pad = 5;
    const textW = Math.max(...lines.map((t) => ctx.measureText(t).width));
    const boxH = lines.length * lineH + pad;
    const boxW = textW + pad * 2;
    const bx = cx - boxW / 2;
    const by = cy - boxH / 2;

    ctx.fillStyle = slot.mergedStatus === 'AVAILABLE' ? 'rgba(46,125,50,0.88)' : 'rgba(0,0,0,0.72)';
    ctx.fillRect(bx, by, boxW, boxH);
    ctx.fillStyle = '#fff';
    lines.forEach((t, i) => {
      ctx.fillText(t, bx + pad, by + pad + 12 + i * lineH);
    });
  });
}

function onLiveStreamResize() {
  if (liveSlotsCache.length) drawLiveOverlay(liveSlotsCache);
}

window.addEventListener('resize', () => {
  if (state.view === 'detail') onLiveStreamResize();
});

async function openDetail(areaId) {
  state.selectedArea = state.areas.find((a) => a.areaId === areaId) || { areaId, areaName: areaId };
  showView('detail');
  document.getElementById('detailTitle').textContent = state.selectedArea.areaName || areaId;
  startLiveVideo('liveStream');
  await refreshDetail();
  if (detailTimer) clearInterval(detailTimer);
  detailTimer = setInterval(refreshDetail, 5000);
}

async function refreshPlates(areaId) {
  const entryEl = document.getElementById('entryPlatePanel');
  const activeEl = document.getElementById('activePlatePanel');
  if (!entryEl) return;
  const res = await api(`/admin/entry-plates?areaId=${encodeURIComponent(areaId)}`);
  if (!res.success) return;
  const data = res.data || {};
  entryEl.innerHTML = (data.simulatedPlates || []).map((p) =>
    `<div class="plate-chip">🅿️ Giriş: ${p}<div class="sub">Otopark içinde (simülasyon)</div></div>`
  ).join('');
  const parked = data.parkedVehicles || [];
  activeEl.innerHTML = parked.length
    ? parked.map((v) =>
      `<div class="plate-chip inside">🚗 ${v.licensePlate}<div class="sub">Slot #${v.slotNumber} · ${v.slotId || ''}</div></div>`
    ).join('')
    : '<p class="hint">Henüz eşleşmiş plaka yok (dolu slotlara otomatik atanır)</p>';
}

async function refreshDetail() {
  const areaId = state.selectedArea?.areaId;
  if (!areaId) return;
  const [res, sessRes] = await Promise.all([
    api(`/parking/live?areaId=${encodeURIComponent(areaId)}`),
    api(`/admin/sessions?areaId=${encodeURIComponent(areaId)}`),
  ]);
  if (!res.success) return;
  const live = res.data;
  const sessions = sessRes.success ? (sessRes.data || []) : [];
  const plateBySlot = {};
  sessions.forEach((s) => {
    if (s.slotId && s.licensePlate) plateBySlot[s.slotId] = s.licensePlate;
  });
  document.getElementById('detailMeta').textContent =
    `Toplam ${live.totalSlots} · Boş ${live.availableSlots} · Dolu ${live.occupiedSlots} · Rezerve ${live.reservedSlots} · ${live.lastUpdated || ''}`;

  liveSlotsCache = (live.slots || []).map((s) => ({
    ...s,
    licensePlate: s.licensePlate || plateBySlot[s.slotId] || null,
  }));
  drawLiveOverlay(liveSlotsCache);
  await refreshPlates(areaId);

  const grid = document.getElementById('slotGrid');
  grid.innerHTML = liveSlotsCache.map((s) => {
    let cls = 'free';
    if (s.mergedStatus === 'OCCUPIED') cls = 'occ';
    else if (s.mergedStatus === 'RESERVED') cls = 'res';
    const label = s.displayLabel || (s.mergedStatus === 'AVAILABLE' ? 'BOŞ' : s.mergedStatus);
    const plate = s.licensePlate ? `<div class="hint">${s.licensePlate}</div>` : '';
    const slotId = s.slotId ? `<div class="hint">${s.slotId}</div>` : '';
    return `<div class="slot-card ${cls}">#${s.slotNumber}<br>${label}${plate}${slotId}</div>`;
  }).join('');
}

async function assignManualPlate() {
  const areaId = state.selectedArea?.areaId;
  const slotId = document.getElementById('manualSlotId').value.trim();
  const plate = document.getElementById('manualPlate').value.trim();
  if (!areaId || !slotId || !plate) { toast('Slot ID ve plaka gerekli'); return; }
  const res = await api('/admin/manual-plate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ areaId, slotId, licensePlate: plate }),
  });
  toast(res.success ? 'Plaka atandı' : (res.message || 'Hata'));
  if (res.success) refreshDetail();
}

function backToList() {
  if (detailTimer) { clearInterval(detailTimer); detailTimer = null; }
  stopLiveVideo();
  showView('list');
  loadAreas();
}

// ─── Init ───
document.addEventListener('DOMContentLoaded', () => {
  initCanvasEvents();
  loadVideoOptions();
  loadAreas();
});
