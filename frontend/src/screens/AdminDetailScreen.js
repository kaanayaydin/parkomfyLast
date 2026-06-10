import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Image, ActivityIndicator, Alert,
} from 'react-native';
import LiveCameraView from '../components/LiveCameraView';
import {
  API_HOST,
  getLiveParkingStatus,
  getLiveImageUrl,
  getAdminReservations,
  getAdminSessions,
  scanLiveCamera,
  mergedStatusColor,
  formatDateTime,
  reservationStatusLabel,
} from '../config/api';

const AdminDetailScreen = ({ otopark, onBack }) => {
  const areaId = otopark.areaId;
  const lotKey = otopark.lotKey || 'yen1';
  const [live, setLive] = useState(null);
  const [reservations, setReservations] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [imageKey, setImageKey] = useState(Date.now());
  const [scanning, setScanning] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadAll = useCallback(async () => {
    try {
      const [liveRes, resRes, sessRes] = await Promise.all([
        getLiveParkingStatus(areaId),
        getAdminReservations(areaId),
        getAdminSessions(areaId),
      ]);
      if (liveRes.success) {
        const reservationPlateBySlot = {};
        (resRes.success ? resRes.data || [] : []).forEach((r) => {
          if (r.slotId && r.licensePlate && (r.status === 'RESERVED' || r.status === 'ACTIVE')) {
            reservationPlateBySlot[r.slotId] = r.licensePlate;
          }
        });
        const sessionPlateBySlot = {};
        (sessRes.success ? sessRes.data || [] : []).forEach((s) => {
          if (s.slotId && s.licensePlate) sessionPlateBySlot[s.slotId] = s.licensePlate;
        });
        const slots = (liveRes.data.slots || []).map((s) => {
          const showPlate = s.mergedStatus === 'OCCUPIED' || s.mergedStatus === 'RESERVED';
          let plate = null;
          if (showPlate) {
            plate = s.licensePlate
              || (s.mergedStatus === 'RESERVED' ? reservationPlateBySlot[s.slotId] : null)
              || sessionPlateBySlot[s.slotId]
              || null;
          }
          return { ...s, licensePlate: plate };
        });
        setLive({ ...liveRes.data, slots });
      }
      if (resRes.success) setReservations(resRes.data || []);
      if (sessRes.success) setSessions(sessRes.data || []);
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

  const slotLabel = (s) => s.displayLabel || (s.mergedStatus === 'AVAILABLE' ? 'BOŞ' : s.mergedStatus);

  return (
    <ScrollView style={styles.container}>
      <TouchableOpacity onPress={onBack}>
        <Text style={styles.back}>← Geri</Text>
      </TouchableOpacity>
      <Text style={styles.title}>{otopark.name}</Text>
      <Text style={styles.sub}>Admin · {areaId} · {lotKey} · {API_HOST}:8080</Text>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Canlı Otopark Kamerası</Text>
        <LiveCameraView lotKey={lotKey} height={220} label={`Canlı Kamera (${lotKey})`} />
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Giriş / Çıkış Kameraları</Text>
        <Text style={styles.note}>Plaka tanıma canlı çalışır (kutu + plaka + güven skoru)</Text>
        <View style={{ marginTop: 10 }}>
          <LiveCameraView lotKey="giris" height={180} label="Giriş Kamerası" />
        </View>
        <View style={{ marginTop: 14 }}>
          <LiveCameraView lotKey="cikis" height={180} label="Çıkış Kamerası" />
        </View>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#1A237E" style={{ marginVertical: 24 }} />
      ) : live ? (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Canlı Doluluk</Text>
          <Text style={styles.row}>Toplam: {live.totalSlots} · Boş: {live.availableSlots}</Text>
          <Text style={styles.row}>Dolu: {live.occupiedSlots} · Rezerve: {live.reservedSlots}</Text>
          <Text style={styles.row}>Doluluk: {(live.occupancyRate * 100).toFixed(0)}%</Text>
        </View>
      ) : (
        <Text style={styles.hint}>Canlı veri alınamadı</Text>
      )}

      {live?.slots?.length > 0 && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Slot Durumu (Araç / Rezervasyon)</Text>
          <View style={styles.slotGrid}>
            {live.slots.map((s) => (
              <View
                key={s.slotId}
                style={[styles.slotChip, { backgroundColor: mergedStatusColor(s.mergedStatus) }]}
              >
                <Text style={styles.slotChipNum}>#{s.slotNumber}</Text>
                <Text style={styles.slotChipStatus}>{slotLabel(s)}</Text>
                {s.licensePlate ? (
                  <Text style={styles.slotChipPlate} numberOfLines={1}>🚗 {s.licensePlate}</Text>
                ) : s.mergedStatus === 'RESERVED' ? (
                  <Text style={styles.slotChipPlate}>Rezerve</Text>
                ) : null}
              </View>
            ))}
          </View>
        </View>
      )}

      <View style={styles.card}>
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

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Aktif Oturumlar ({sessions.length})</Text>
        {sessions.length === 0 ? (
          <Text style={styles.muted}>Aktif oturum yok</Text>
        ) : (
          sessions.map((s) => (
            <Text key={s.sessionId} style={styles.listItem}>
              🚗 {s.licensePlate || '?'} → Slot #{(s.slotId || '').split('-').pop()} ({s.slotId})
            </Text>
          ))
        )}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Rezervasyonlar ({reservations.length})</Text>
        {reservations.length === 0 ? (
          <Text style={styles.muted}>Rezervasyon yok</Text>
        ) : (
          reservations.map((r) => (
            <View key={r.reservationId} style={styles.resItem}>
              <Text style={styles.listItem}>
                Slot #{(r.slotId || '').split('-').pop()} — {r.licensePlate}
              </Text>
              <Text style={styles.resSub}>
                {reservationStatusLabel(r.status)} · {formatDateTime(r.startTime)} → {formatDateTime(r.endTime)}
              </Text>
            </View>
          ))
        )}
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
  btn: {
    backgroundColor: '#1A237E',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 10,
  },
  btnText: { color: '#FFF', fontWeight: 'bold', fontSize: 13 },
  slotGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  slotChip: {
    width: 90,
    padding: 10,
    borderRadius: 8,
    alignItems: 'center',
    minHeight: 72,
  },
  slotChipNum: { color: '#FFF', fontWeight: 'bold', fontSize: 16 },
  slotChipStatus: { color: '#FFF', fontSize: 10, marginTop: 2, textAlign: 'center' },
  slotChipPlate: { color: '#FFF', fontSize: 9, marginTop: 4, textAlign: 'center' },
  listItem: { fontSize: 13, color: '#444', marginBottom: 4 },
  resItem: { marginBottom: 10, paddingBottom: 8, borderBottomWidth: 1, borderBottomColor: '#EEE' },
  resSub: { fontSize: 11, color: '#888' },
  muted: { color: '#999', fontStyle: 'italic' },
});

export default AdminDetailScreen;
