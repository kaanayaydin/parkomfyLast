import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Image, ActivityIndicator, Alert,
} from 'react-native';
import LiveCameraView from '../components/LiveCameraView';
import {
  API_HOST,
  AREA_IDS,
  getLiveParkingStatus,
  getLiveImageUrl,
  getAdminReservations,
  getAdminSessions,
  getAdminDetections,
  scanLiveCamera,
  mergedStatusColor,
  formatDateTime,
} from '../config/api';

const AdminDetailScreen = ({ otopark, onBack }) => {
  const areaId = otopark.areaId || AREA_IDS[otopark.id] || 'AREA-001';
  const [live, setLive] = useState(null);
  const [reservations, setReservations] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [detections, setDetections] = useState([]);
  const [imageKey, setImageKey] = useState(Date.now());
  const [scanning, setScanning] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadAll = useCallback(async () => {
    try {
      const [liveRes, resRes, sessRes, detRes] = await Promise.all([
        getLiveParkingStatus(areaId),
        getAdminReservations(areaId),
        getAdminSessions(areaId),
        getAdminDetections(areaId),
      ]);
      if (liveRes.success) setLive(liveRes.data);
      if (resRes.success) setReservations(resRes.data || []);
      if (sessRes.success) setSessions(sessRes.data || []);
      if (detRes.success) setDetections(detRes.data || []);
    } catch (e) {
      console.log('Admin load failed:', e.message);
    } finally {
      setLoading(false);
    }
  }, [areaId]);

  useEffect(() => {
    loadAll();
    const t = setInterval(loadAll, 5000);
    return () => clearInterval(t);
  }, [loadAll]);

  const handleLiveScan = async () => {
    setScanning(true);
    try {
      const scanRes = await scanLiveCamera(areaId);
      if (scanRes.success) {
        Alert.alert('Canlı tarama', `${scanRes.data.matchesFound} plaka eşleşmesi`);
        setImageKey(Date.now());
        loadAll();
      } else {
        Alert.alert('Hata', scanRes.message || 'Canlı tarama başarısız');
      }
    } catch {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı');
    } finally {
      setScanning(false);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <TouchableOpacity onPress={onBack}>
        <Text style={styles.back}>← Geri</Text>
      </TouchableOpacity>
      <Text style={styles.title}>{otopark.name}</Text>
      <Text style={styles.sub}>Admin · {areaId} · {API_HOST}:8080</Text>

      {loading ? (
        <ActivityIndicator size="large" color="#1A237E" style={{ marginVertical: 24 }} />
      ) : live ? (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Canlı Doluluk (birleşik)</Text>
          <Text style={styles.row}>Toplam: {live.totalSlots}</Text>
          <Text style={styles.row}>Boş: {live.availableSlots}</Text>
          <Text style={styles.row}>Dolu: {live.occupiedSlots}</Text>
          <Text style={styles.row}>Rezerve: {live.reservedSlots}</Text>
          <Text style={styles.row}>
            Doluluk: {(live.occupancyRate * 100).toFixed(0)}%
          </Text>
        </View>
      ) : (
        <Text style={styles.hint}>Canlı veri alınamadı</Text>
      )}

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Canlı Kamera (loop1.mp4 simülasyonu)</Text>
        <LiveCameraView height={220} />
        <TouchableOpacity style={styles.btn} onPress={handleLiveScan} disabled={scanning}>
          <Text style={styles.btnText}>{scanning ? 'Taranıyor...' : 'Anlık Kareyi Tara'}</Text>
        </TouchableOpacity>
        <Text style={styles.note}>Slot overlay (son tarama):</Text>
        <Image
          source={{ uri: `${getLiveImageUrl(areaId)}&k=${imageKey}` }}
          style={styles.liveImage}
          resizeMode="contain"
        />
      </View>

      {live?.slots?.length > 0 && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Slot Durumu</Text>
          <View style={styles.slotGrid}>
            {live.slots.map((s) => (
              <View
                key={s.slotId}
                style={[styles.slotChip, { backgroundColor: mergedStatusColor(s.mergedStatus) }]}
              >
                <Text style={styles.slotChipNum}>{s.slotNumber}</Text>
                <Text style={styles.slotChipStatus}>{s.mergedStatus}</Text>
                {s.licensePlate ? (
                  <Text style={styles.slotChipPlate} numberOfLines={1}>{s.licensePlate}</Text>
                ) : null}
              </View>
            ))}
          </View>
        </View>
      )}

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Aktif Oturumlar ({sessions.length})</Text>
        {sessions.length === 0 ? (
          <Text style={styles.muted}>Aktif oturum yok</Text>
        ) : (
          sessions.map((s) => (
            <Text key={s.sessionId} style={styles.listItem}>
              {s.licensePlate || '?'} → {s.slotId}
            </Text>
          ))
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Rezervasyonlar ({reservations.length})</Text>
        {reservations.slice(0, 8).map((r) => (
          <Text key={r.reservationId} style={styles.listItem}>
            {r.licensePlate} · {r.slotId} · {formatDateTime(r.startTime)}
          </Text>
        ))}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Son Tespitler ({detections.length})</Text>
        {detections.slice(0, 6).map((d) => (
          <Text key={d.detectionId} style={styles.listItem}>
            {d.detectionType} · {d.licensePlate || '-'} · {d.slotId || '-'} · {(d.confidence * 100).toFixed(0)}%
          </Text>
        ))}
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, backgroundColor: '#F5F5F5' },
  back: { color: '#1A237E', fontWeight: 'bold', marginBottom: 16 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A237E' },
  sub: { color: '#666', marginBottom: 16 },
  card: {
    backgroundColor: '#FFF',
    padding: 16,
    borderRadius: 12,
    marginBottom: 14,
  },
  cardTitle: { fontSize: 16, fontWeight: 'bold', color: '#1A237E', marginBottom: 10 },
  row: { fontSize: 15, marginBottom: 6, color: '#333' },
  hint: { color: '#999', fontStyle: 'italic', marginBottom: 12 },
  liveImage: { width: '100%', height: 200, backgroundColor: '#EEE', borderRadius: 8 },
  note: { fontSize: 11, color: '#888', marginTop: 8 },
  btnRow: { flexDirection: 'row', gap: 8, marginTop: 12 },
  btn: {
    flex: 1,
    backgroundColor: '#1A237E',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  btnAlt: { backgroundColor: '#3949AB' },
  btnText: { color: '#FFF', fontWeight: 'bold', fontSize: 13 },
  slotGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  slotChip: {
    width: 72,
    padding: 8,
    borderRadius: 8,
    alignItems: 'center',
  },
  slotChipNum: { color: '#FFF', fontWeight: 'bold', fontSize: 16 },
  slotChipStatus: { color: '#FFF', fontSize: 9, marginTop: 2 },
  slotChipPlate: { color: '#FFF', fontSize: 8, marginTop: 2 },
  listItem: { fontSize: 13, color: '#444', marginBottom: 6 },
  muted: { color: '#999', fontStyle: 'italic' },
});

export default AdminDetailScreen;
