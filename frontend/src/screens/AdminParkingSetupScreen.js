import React, { useState, useRef, useMemo } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput,
  Image, Alert, ActivityIndicator, Dimensions, PanResponder,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import LiveCameraView from '../components/LiveCameraView';
import {
  createParkingArea,
  predictSlotLayout,
  predictSlotsFromLiveCamera,
  saveSlotCalibration,
} from '../config/api';

const { width: SCREEN_W } = Dimensions.get('window');
const CORNER_HANDLE = 36;

function DraggableCorner({
  normX, normY, displayW, displayH, color, active, onDrag,
}) {
  const onDragRef = useRef(onDrag);
  onDragRef.current = onDrag;
  const posRef = useRef({ x: normX, y: normY });
  posRef.current = { x: normX, y: normY };
  const originRef = useRef({ x: normX, y: normY });

  const panResponder = useMemo(
    () => PanResponder.create({
      onStartShouldSetPanResponder: () => active,
      onMoveShouldSetPanResponder: () => active,
      onPanResponderGrant: () => {
        originRef.current = { ...posRef.current };
      },
      onPanResponderTerminationRequest: () => false,
      onShouldBlockNativeResponder: () => true,
      onPanResponderMove: (_, gesture) => {
        const nx = Math.max(0, Math.min(1, originRef.current.x + gesture.dx / displayW));
        const ny = Math.max(0, Math.min(1, originRef.current.y + gesture.dy / displayH));
        onDragRef.current(
          Math.round(nx * 1000) / 1000,
          Math.round(ny * 1000) / 1000,
        );
      },
    }),
    [active, displayW, displayH],
  );

  const px = normX * displayW;
  const py = normY * displayH;

  return (
    <View
      {...(active ? panResponder.panHandlers : {})}
      style={{
        position: 'absolute',
        left: px - CORNER_HANDLE / 2,
        top: py - CORNER_HANDLE / 2,
        width: CORNER_HANDLE,
        height: CORNER_HANDLE,
        borderRadius: CORNER_HANDLE / 2,
        backgroundColor: color,
        borderWidth: active ? 3 : 2,
        borderColor: '#FFF',
        zIndex: active ? 30 : 8,
        elevation: active ? 8 : 2,
      }}
    />
  );
}

function PolyEdge({ x1, y1, x2, y2, color, thickness }) {
  const dx = x2 - x1;
  const dy = y2 - y1;
  const len = Math.hypot(dx, dy);
  if (len < 1) return null;
  const angle = (Math.atan2(dy, dx) * 180) / Math.PI;
  const midX = (x1 + x2) / 2;
  const midY = (y1 + y2) / 2;
  return (
    <View
      style={{
        position: 'absolute',
        left: midX - len / 2,
        top: midY - thickness / 2,
        width: len,
        height: thickness,
        backgroundColor: color,
        transform: [{ rotate: `${angle}deg` }],
      }}
    />
  );
}

function renumberSlots(slotList) {
  return slotList.map((s, i) => ({ ...s, slotNumber: i + 1 }));
}

function createDefaultSlot(slotNumber, totalExisting) {
  const cols = 3;
  const row = Math.floor(totalExisting / cols);
  const col = totalExisting % cols;
  const margin = 0.06;
  const cellW = (1 - 2 * margin) / cols;
  const cellH = 0.18;
  const x0 = margin + col * cellW;
  const y0 = Math.min(0.72, 0.42 + row * cellH);
  const inset = 0.015;
  return {
    slotNumber,
    occupied: false,
    manual: true,
    corners: [
      { x: x0 + inset, y: y0 + inset },
      { x: x0 + cellW - inset, y: y0 + inset },
      { x: x0 + cellW - inset, y: y0 + cellH - inset },
      { x: x0 + inset, y: y0 + cellH - inset },
    ],
  };
}

function applyPredictionToState(data, setFrozenImageUri, setImageUri, setImageSize) {
  const slots = data?.slots ?? (Array.isArray(data) ? data : []);
  const w = data?.imageWidth || 1280;
  const h = data?.imageHeight || 720;
  if (data?.imageBase64) {
    const uri = `data:image/jpeg;base64,${data.imageBase64}`;
    setFrozenImageUri(uri);
    setImageUri(uri);
    setImageSize({ w, h });
  }
  return slots.map((s, i) => ({
    slotNumber: s.slotNumber || i + 1,
    corners: (s.corners || []).map((c) => ({ x: c.x, y: c.y })),
    occupied: s.occupied,
    confidence: s.confidence,
  }));
}

function SlotOverlay({ imageUri, slots, selectedIndex, imageSize, onCornerChange }) {
  const displayW = SCREEN_W - 40;
  if (!imageUri) return <Text style={styles.hint}>Referans kare yükleniyor…</Text>;

  const aspect = imageSize?.w && imageSize?.h ? imageSize.h / imageSize.w : 9 / 16;
  const displayH = displayW * aspect;

  return (
    <View style={{ width: displayW, height: displayH, position: 'relative', backgroundColor: '#111', overflow: 'hidden' }}>
      <Image
        source={{ uri: imageUri }}
        style={{ width: displayW, height: displayH }}
        resizeMode="stretch"
      />
      <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
        {slots.map((slot, idx) => {
          const corners = slot.corners || [];
          if (corners.length < 4) return null;
          const isSelected = idx === selectedIndex;
          const color = isSelected
            ? '#FFEB3B'
            : (slot.manual ? '#2196F3' : (slot.occupied ? '#F44336' : '#4CAF50'));
          const stroke = isSelected ? 3 : 2;
          const pts = corners.map((c) => ({
            x: (c.x || 0) * displayW,
            y: (c.y || 0) * displayH,
          }));
          const cx = pts.reduce((s, p) => s + p.x, 0) / 4;
          const cy = pts.reduce((s, p) => s + p.y, 0) / 4;
          return (
            <View key={`slot-${idx}`} pointerEvents="box-none" style={StyleSheet.absoluteFill}>
              <View pointerEvents="none" style={StyleSheet.absoluteFill}>
                {[0, 1, 2, 3].map((i) => (
                  <PolyEdge
                    key={`edge-${idx}-${i}`}
                    x1={pts[i].x}
                    y1={pts[i].y}
                    x2={pts[(i + 1) % 4].x}
                    y2={pts[(i + 1) % 4].y}
                    color={color}
                    thickness={stroke}
                  />
                ))}
                <Text
                  style={[
                    styles.slotLabel,
                    {
                      position: 'absolute',
                      left: cx - 14,
                      top: cy - 8,
                      color: isSelected ? '#000' : '#FFF',
                      backgroundColor: isSelected ? 'rgba(255,235,59,0.85)' : 'rgba(0,0,0,0.55)',
                      borderRadius: 4,
                      overflow: 'hidden',
                    },
                  ]}
                >
                  #{slot.slotNumber}
                </Text>
              </View>
              {corners.map((c, ci) => (
                <DraggableCorner
                  key={`corner-${idx}-${ci}`}
                  normX={c.x || 0}
                  normY={c.y || 0}
                  displayW={displayW}
                  displayH={displayH}
                  color={color}
                  active={isSelected}
                  onDrag={(nx, ny) => onCornerChange?.(idx, ci, nx, ny)}
                />
              ))}
            </View>
          );
        })}
      </View>
    </View>
  );
}

function CornerEditor({ slot }) {
  if (!slot) return <Text style={styles.hint}>Düzenlemek için bir slot seçin</Text>;
  const labels = ['Sol-Üst', 'Sağ-Üst', 'Sağ-Alt', 'Sol-Alt'];

  return (
    <View>
      <Text style={styles.editorTitle}>Slot #{slot.slotNumber}</Text>
      <Text style={styles.dragHint}>Sarı köşe noktalarını parmağınızla sürükleyerek hizalayın</Text>
      {slot.corners.map((c, i) => (
        <Text key={i} style={styles.cornerCoord}>
          {labels[i]}: {c.x?.toFixed(3)}, {c.y?.toFixed(3)}
        </Text>
      ))}
    </View>
  );
}

const AdminParkingSetupScreen = ({ onBack, onDone }) => {
  const [step, setStep] = useState(1);
  const [areaName, setAreaName] = useState('');
  const [address, setAddress] = useState('');
  const [lotKey, setLotKey] = useState('');
  const [areaId, setAreaId] = useState(null);
  const [imageUri, setImageUri] = useState(null);
  const [frozenImageUri, setFrozenImageUri] = useState(null);
  const [imageSize, setImageSize] = useState({ w: 1280, h: 720 });
  const [slots, setSlots] = useState([]);
  const [selectedSlot, setSelectedSlot] = useState(0);
  const [loading, setLoading] = useState(false);
  const [useLiveCamera, setUseLiveCamera] = useState(true);

  const pickImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.75,
      allowsEditing: false,
    });
    if (result.canceled || !result.assets?.[0]) return;
    const asset = result.assets[0];
    setImageUri(asset.uri);
    setImageSize({ w: asset.width || 1280, h: asset.height || 720 });
  };

  const takePhoto = async () => {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) {
      Alert.alert('İzin', 'Kamera izni gerekli');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({ quality: 0.75 });
    if (result.canceled || !result.assets?.[0]) return;
    const asset = result.assets[0];
    setImageUri(asset.uri);
    setImageSize({ w: asset.width || 1280, h: asset.height || 720 });
  };

  const handleCreateArea = async () => {
    if (!areaName.trim()) {
      Alert.alert('Eksik', 'Otopark adı girin');
      return;
    }
    setLoading(true);
    try {
      const res = await createParkingArea({
        areaName: areaName.trim(),
        address: address.trim(),
        lotKey: lotKey.trim() || undefined,
      });
      if (res.success) {
        setAreaId(res.data.areaId);
        setLotKey(res.data.lotKey);
        setStep(2);
      } else {
        Alert.alert('Hata', res.message);
      }
    } catch {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı');
    } finally {
      setLoading(false);
    }
  };

  const freezeSnapshot = (uri) => {
    setFrozenImageUri(uri);
    setImageUri(uri);
    Image.getSize(
      uri,
      (w, h) => setImageSize({ w, h }),
      () => setImageSize({ w: 1280, h: 720 })
    );
  };

  const goToCalibration = (mappedSlots, message) => {
    setSlots(mappedSlots);
    setSelectedSlot(0);
    setStep(3);
    if (message) Alert.alert('Kalibrasyon', message);
  };

  const handlePredictFromLive = async () => {
    setLoading(true);
    setUseLiveCamera(true);
    try {
      const res = await predictSlotsFromLiveCamera();
      const slotList = res.data?.slots ?? (Array.isArray(res.data) ? res.data : []);
      if (res.success) {
        const mapped = applyPredictionToState(res.data, setFrozenImageUri, setImageUri, setImageSize);
        if (slotList.length) {
          goToCalibration(mapped, `${slotList.length} slot — modelin işlediği kare sabitlendi`);
        } else {
          Alert.alert(
            'Slot bulunamadı',
            'Model slot tespit edemedi. Kare sabitlendi; elle ekleyebilirsiniz.',
            [{ text: 'Manuel Ekle', onPress: () => goToCalibration([], null) }]
          );
        }
      } else {
        Alert.alert('Slot bulunamadı', res.message || 'Canlı kamera veya model hazır değil');
      }
    } catch (e) {
      Alert.alert('Hata', e?.message || 'Canlı kamera bağlantısı başarısız');
    } finally {
      setLoading(false);
    }
  };

  const handlePredict = async () => {
    if (!imageUri) {
      Alert.alert('Fotoğraf', 'Önce otopark fotoğrafı seçin veya canlı kamerayı kullanın');
      return;
    }
    setLoading(true);
    setUseLiveCamera(false);
    freezeSnapshot(imageUri);
    try {
      const res = await predictSlotLayout(imageUri);
      const slotList = res.data?.slots ?? (Array.isArray(res.data) ? res.data : []);
      if (res.success) {
        const mapped = applyPredictionToState(res.data, setFrozenImageUri, setImageUri, setImageSize);
        if (!res.data?.imageBase64) {
          freezeSnapshot(imageUri);
        }
        if (slotList.length) {
          goToCalibration(mapped, `${slotList.length} slot ön tahmini alındı (Bos/Dolu modeli)`);
        } else {
          Alert.alert(
            'Slot bulunamadı',
            'Model slot tespit edemedi. Elle ekleyebilirsiniz.',
            [{ text: 'Manuel Ekle', onPress: () => goToCalibration([], null) }]
          );
        }
      } else {
        Alert.alert(
          'Slot bulunamadı',
          res.message
            || 'Model yanıt vermedi. Spring Boot (8080) + gRPC (50051) + aynı Wi-Fi kontrol edin.'
        );
      }
    } catch (e) {
      Alert.alert('Bağlantı hatası', `${e?.message || 'Sunucuya ulaşılamadı'}. API IP: api.js içindeki 192.168.1.101`);
    } finally {
      setLoading(false);
    }
  };

  const handleAddSlot = () => {
    const newSlot = createDefaultSlot(slots.length + 1, slots.length);
    const next = [...slots, newSlot];
    setSlots(next);
    setSelectedSlot(next.length - 1);
  };

  const handleDeleteSlot = () => {
    if (slots.length === 0) return;
    const num = slots[selectedSlot]?.slotNumber;
    Alert.alert('Slot sil', `#${num} silinsin mi?`, [
      { text: 'İptal', style: 'cancel' },
      {
        text: 'Sil',
        style: 'destructive',
        onPress: () => {
          const next = renumberSlots(slots.filter((_, i) => i !== selectedSlot));
          setSlots(next);
          setSelectedSlot(next.length === 0 ? 0 : Math.min(selectedSlot, next.length - 1));
        },
      },
    ]);
  };

  const handleSave = async () => {
    if (!areaId || slots.length === 0) return;
    setLoading(true);
    try {
      const res = await saveSlotCalibration({
        areaId,
        lotKey,
        imageWidth: imageSize.w,
        imageHeight: imageSize.h,
        slots: slots.map((s) => ({
          slotNumber: s.slotNumber,
          corners: s.corners,
          occupied: !!s.occupied,
        })),
      });
      if (res.success) {
        Alert.alert('Kaydedildi', `${res.data.slotCount} slot kalibre edildi`);
        onDone?.(res.data);
        onBack?.();
      } else {
        Alert.alert('Hata', res.message);
      }
    } catch {
      Alert.alert('Hata', 'Kayıt başarısız');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <TouchableOpacity onPress={onBack}>
        <Text style={styles.back}>← Geri</Text>
      </TouchableOpacity>
      <Text style={styles.title}>Yeni Otopark Kaydı</Text>
      <Text style={styles.step}>Adım {step}/3 — {step === 1 ? 'Bilgi' : step === 2 ? 'Fotoğraf' : 'Kalibrasyon'}</Text>

      {step === 1 && (
        <View style={styles.card}>
          <TextInput style={styles.input} placeholder="Otopark adı *" value={areaName} onChangeText={setAreaName} />
          <TextInput style={styles.input} placeholder="Adres" value={address} onChangeText={setAddress} />
          <TextInput style={styles.input} placeholder="Lot anahtarı (opsiyonel, örn: istasyon4)" value={lotKey} onChangeText={setLotKey} />
          <TouchableOpacity style={styles.btn} onPress={handleCreateArea} disabled={loading}>
            <Text style={styles.btnText}>{loading ? '...' : 'Otopark Oluştur →'}</Text>
          </TouchableOpacity>
        </View>
      )}

      {step === 2 && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Canlı kamera simülasyonu ({areaId})</Text>
          <LiveCameraView height={200} lotKey={lotKey || 'yen1'} />
          {step === 2 && (
            <>
              <TouchableOpacity style={[styles.btn, styles.liveBtn]} onPress={handlePredictFromLive} disabled={loading}>
                {loading ? <ActivityIndicator color="#FFF" /> : (
                  <Text style={styles.btnText}>Canlı Görüntüden Slot Tahmini</Text>
                )}
              </TouchableOpacity>
              {loading && <Text style={styles.waitHint}>Model işliyor…</Text>}
            </>
          )}
          <Text style={styles.orText}>— veya statik fotoğraf —</Text>
          <View style={styles.btnRow}>
            <TouchableOpacity style={[styles.btn, styles.btnHalf, styles.btnAlt]} onPress={takePhoto}>
              <Text style={styles.btnText}>Telefon Kamerası</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.btn, styles.btnHalf, styles.btnAlt]} onPress={pickImage}>
              <Text style={styles.btnText}>Galeri</Text>
            </TouchableOpacity>
          </View>
          {imageUri && !useLiveCamera && (
            <Image source={{ uri: imageUri }} style={styles.preview} resizeMode="contain" />
          )}
          {step === 2 && imageUri && !useLiveCamera && (
            <TouchableOpacity style={styles.btn} onPress={handlePredict} disabled={loading}>
              <Text style={styles.btnText}>Fotoğraftan Slot Tahmini</Text>
            </TouchableOpacity>
          )}
        </View>
      )}

      {step === 3 && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Köşeleri düzenle → Onayla</Text>
          <Text style={styles.frozenHint}>Referans kare donduruldu (video akışı kapalı)</Text>
          <Text style={styles.slotCountHint}>
            {slots.length} slot — mavi: elle eklenen, yeşil/kırmızı: model tahmini
          </Text>
          <SlotOverlay
            imageUri={frozenImageUri || imageUri}
            slots={slots}
            selectedIndex={selectedSlot}
            imageSize={imageSize}
            onCornerChange={(slotIdx, cornerIdx, x, y) => {
              const next = [...slots];
              const slot = { ...next[slotIdx], corners: [...next[slotIdx].corners] };
              slot.corners[cornerIdx] = { x, y };
              next[slotIdx] = slot;
              setSlots(next);
            }}
          />
          <View style={styles.slotActions}>
            <TouchableOpacity style={[styles.btn, styles.btnHalf, styles.addBtn]} onPress={handleAddSlot}>
              <Text style={styles.btnText}>+ Slot Ekle</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.btn, styles.btnHalf, styles.deleteBtn]}
              onPress={handleDeleteSlot}
              disabled={slots.length === 0}
            >
              <Text style={styles.btnText}>Seçili Sil</Text>
            </TouchableOpacity>
          </View>
          {slots.length === 0 ? (
            <Text style={styles.hint}>Henüz slot yok — «+ Slot Ekle» ile başlayın</Text>
          ) : (
            <>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.slotPicker}>
                {slots.map((s, i) => (
                  <TouchableOpacity
                    key={`slot-chip-${i}`}
                    style={[styles.slotChip, selectedSlot === i && styles.slotChipActive]}
                    onPress={() => setSelectedSlot(i)}
                  >
                    <Text style={[styles.slotChipText, selectedSlot === i && { color: '#FFF' }]}>
                      #{s.slotNumber}{s.manual ? ' ✎' : ''}
                    </Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
              <CornerEditor slot={slots[selectedSlot]} />
            </>
          )}
          <TouchableOpacity
            style={[styles.btn, styles.saveBtn]}
            onPress={handleSave}
            disabled={loading || slots.length === 0}
          >
            <Text style={styles.btnText}>{loading ? 'Kaydediliyor...' : '✓ Onayla ve Kaydet'}</Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, backgroundColor: '#F5F6FA' },
  back: { color: '#1A237E', fontWeight: 'bold', marginBottom: 8 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A237E' },
  step: { color: '#666', marginBottom: 16 },
  card: { backgroundColor: '#FFF', padding: 16, borderRadius: 12, marginBottom: 14 },
  cardTitle: { fontWeight: 'bold', color: '#1A237E', marginBottom: 12 },
  input: { borderWidth: 1, borderColor: '#DDD', borderRadius: 10, padding: 14, marginBottom: 10 },
  btn: { backgroundColor: '#1A237E', padding: 14, borderRadius: 10, alignItems: 'center', marginTop: 8 },
  saveBtn: { backgroundColor: '#2E7D32' },
  btnText: { color: '#FFF', fontWeight: 'bold' },
  btnRow: { flexDirection: 'row', gap: 8 },
  btnHalf: { flex: 1 },
  preview: { width: '100%', height: 180, marginVertical: 10, backgroundColor: '#EEE' },
  slotLabel: { color: '#FFF', fontWeight: 'bold', fontSize: 12, padding: 2 },
  slotPicker: { marginVertical: 12 },
  slotChip: { padding: 10, backgroundColor: '#E8EAF6', borderRadius: 8, marginRight: 8 },
  slotChipActive: { backgroundColor: '#1A237E' },
  slotChipText: { color: '#1A237E', fontWeight: 'bold' },
  frozenHint: { fontSize: 12, color: '#888', marginBottom: 6 },
  slotCountHint: { fontSize: 12, color: '#666', marginBottom: 10 },
  slotActions: { flexDirection: 'row', gap: 8, marginTop: 4 },
  addBtn: { backgroundColor: '#1565C0', marginTop: 0 },
  deleteBtn: { backgroundColor: '#C62828', marginTop: 0, opacity: 1 },
  editorTitle: { fontWeight: '600', marginBottom: 4, color: '#333' },
  dragHint: { fontSize: 13, color: '#1565C0', marginBottom: 8 },
  cornerCoord: { fontSize: 12, color: '#666', fontFamily: 'monospace', marginBottom: 2 },
  hint: { color: '#999', fontStyle: 'italic' },
  waitHint: { color: '#666', textAlign: 'center', marginTop: 10, fontSize: 13 },
  liveBtn: { backgroundColor: '#C62828', marginTop: 12 },
  orText: { textAlign: 'center', color: '#999', marginVertical: 12, fontSize: 12 },
});

export default AdminParkingSetupScreen;
