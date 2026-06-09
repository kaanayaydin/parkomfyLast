import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator,
} from 'react-native';
import { listParkingAreas } from '../config/api';

const AdminScreen = ({ onEnterDetail, onNewParking }) => {
  const [areas, setAreas] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const res = await listParkingAreas();
      if (res.success) setAreas(res.data || []);
    } catch {
      setAreas([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.header}>Yönetici Paneli</Text>

      <TouchableOpacity style={styles.newBtn} onPress={onNewParking}>
        <Text style={styles.newBtnText}>+ Yeni Otopark Kaydı (Slot Kalibrasyonu)</Text>
      </TouchableOpacity>

      {loading ? (
        <ActivityIndicator color="#1A237E" style={{ marginTop: 24 }} />
      ) : areas.length === 0 ? (
        <Text style={styles.empty}>Kayıtlı otopark yok</Text>
      ) : (
        areas.map((item) => (
          <TouchableOpacity
            key={item.areaId}
            style={styles.card}
            onPress={() => onEnterDetail({
              id: item.lotKey || item.areaId,
              name: item.areaName,
              areaId: item.areaId,
              calibrated: item.calibrated,
            })}
          >
            <View>
              <Text style={styles.cardTitle}>{item.areaName}</Text>
              <Text style={styles.cardSub}>
                {item.areaId} · {item.slotCount} slot
                {item.calibrated ? ' · ✓ Kalibre' : ' · Kalibrasyon bekliyor'}
              </Text>
            </View>
            <Text style={styles.arrow}>→</Text>
          </TouchableOpacity>
        ))
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F6FA', padding: 20 },
  header: { fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 },
  newBtn: {
    backgroundColor: '#2E7D32',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: 20,
  },
  newBtnText: { color: '#FFF', fontWeight: 'bold', fontSize: 15 },
  card: {
    backgroundColor: '#FFF', padding: 20, borderRadius: 15,
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    marginBottom: 15, elevation: 3,
  },
  cardTitle: { fontSize: 18, fontWeight: 'bold' },
  cardSub: { fontSize: 12, color: '#888', marginTop: 4 },
  arrow: { fontSize: 20, fontWeight: 'bold', color: '#1A237E' },
  empty: { color: '#999', textAlign: 'center', marginTop: 20 },
});

export default AdminScreen;
