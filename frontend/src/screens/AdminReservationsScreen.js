import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity,
} from 'react-native';
import { getAdminReservations, formatDateTime, reservationStatusLabel, listParkingAreas } from '../config/api';

const AdminReservationsScreen = () => {
  const [reservations, setReservations] = useState([]);
  const [areas, setAreas] = useState([]);
  const [filterAreaId, setFilterAreaId] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [resRes, areasRes] = await Promise.all([
        getAdminReservations(filterAreaId || undefined),
        listParkingAreas(),
      ]);
      if (areasRes.success) setAreas(areasRes.data || []);
      if (resRes.success) setReservations(resRes.data || []);
      else setReservations([]);
    } catch {
      setReservations([]);
    } finally {
      setLoading(false);
    }
  }, [filterAreaId]);

  useEffect(() => { load(); }, [load]);

  const areaName = (areaId) => areas.find((a) => a.areaId === areaId)?.areaName || areaId;

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.header}>Tüm Rezervasyonlar</Text>
      <Text style={styles.hint}>Tüm otoparklardaki slot rezervasyonları</Text>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterRow}>
        <TouchableOpacity
          style={[styles.filterChip, !filterAreaId && styles.filterChipActive]}
          onPress={() => setFilterAreaId('')}
        >
          <Text style={[styles.filterText, !filterAreaId && styles.filterTextActive]}>Tümü</Text>
        </TouchableOpacity>
        {areas.map((a) => (
          <TouchableOpacity
            key={a.areaId}
            style={[styles.filterChip, filterAreaId === a.areaId && styles.filterChipActive]}
            onPress={() => setFilterAreaId(a.areaId)}
          >
            <Text style={[styles.filterText, filterAreaId === a.areaId && styles.filterTextActive]}>
              {a.areaName}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {loading ? (
        <ActivityIndicator color="#1A237E" style={{ marginTop: 24 }} />
      ) : reservations.length === 0 ? (
        <Text style={styles.empty}>Rezervasyon bulunamadı</Text>
      ) : (
        reservations.map((r) => (
          <View key={r.reservationId} style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>{areaName(r.areaId)}</Text>
              <Text style={styles.status}>{reservationStatusLabel(r.status)}</Text>
            </View>
            <Text style={styles.row}>Slot: #{(r.slotId || '').split('-').pop()} · {r.slotId}</Text>
            <Text style={styles.plate}>{r.licensePlate || '-'}</Text>
            <Text style={styles.time}>
              {formatDateTime(r.startTime)} → {formatDateTime(r.endTime)}
            </Text>
            <Text style={styles.fee}>{r.totalFee} TL</Text>
          </View>
        ))
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F6FA', padding: 20 },
  header: { fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginTop: 12 },
  hint: { fontSize: 13, color: '#666', marginBottom: 12 },
  filterRow: { marginBottom: 16, maxHeight: 44 },
  filterChip: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20,
    backgroundColor: '#E8EAF6', marginRight: 8,
  },
  filterChipActive: { backgroundColor: '#1A237E' },
  filterText: { color: '#1A237E', fontWeight: '600', fontSize: 13 },
  filterTextActive: { color: '#FFF' },
  empty: { color: '#999', textAlign: 'center', marginTop: 32 },
  card: {
    backgroundColor: '#FFF', borderRadius: 12, padding: 16, marginBottom: 12, elevation: 2,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  cardTitle: { fontSize: 16, fontWeight: 'bold', color: '#1A237E', flex: 1 },
  status: { fontSize: 12, fontWeight: 'bold', color: '#E65100' },
  row: { fontSize: 13, color: '#666', marginTop: 8 },
  plate: { fontSize: 18, fontWeight: 'bold', color: '#333', marginTop: 6 },
  time: { fontSize: 12, color: '#888', marginTop: 6 },
  fee: { fontSize: 15, fontWeight: 'bold', color: '#1A237E', marginTop: 8 },
});

export default AdminReservationsScreen;
