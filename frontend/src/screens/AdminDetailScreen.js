import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { AREA_IDS, getParkingStatus } from '../config/api';

const AdminDetailScreen = ({ otopark, onBack }) => {
  const [status, setStatus] = useState(null);

  useEffect(() => {
    const areaId = AREA_IDS[otopark.id] || 'AREA-001';
    const load = async () => {
      try {
        const res = await getParkingStatus(areaId);
        if (res.success) setStatus(res.data);
      } catch (e) {
        console.log('Status fetch failed:', e.message);
      }
    };
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [otopark.id]);

  return (
    <ScrollView style={styles.container}>
      <TouchableOpacity onPress={onBack}>
        <Text style={styles.back}>← Geri</Text>
      </TouchableOpacity>
      <Text style={styles.title}>{otopark.name}</Text>
      <Text style={styles.sub}>Java Spring Boot · {AREA_IDS[otopark.id]}</Text>

      {status ? (
        <View style={styles.card}>
          <Text style={styles.row}>Toplam slot: {status.totalSlots}</Text>
          <Text style={styles.row}>Dolu: {status.occupiedSlots}</Text>
          <Text style={styles.row}>Boş: {status.availableSlots}</Text>
          <Text style={styles.row}>
            Doluluk: {(status.occupancyRate * 100).toFixed(0)}%
          </Text>
        </View>
      ) : (
        <Text style={styles.hint}>
          Spring Boot çalışmıyor olabilir (localhost:8080). gRPC CV ayrı port 50051.
        </Text>
      )}

      <Text style={styles.note}>
        Canlı MJPEG artık Flask yerine Java REST + gRPC pipeline kullanır. Slot
        görseli için POST /api/v1/detection/parking-slots-image kullanılabilir.
      </Text>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, backgroundColor: '#F5F5F5' },
  back: { color: '#1A237E', fontWeight: 'bold', marginBottom: 16 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A237E' },
  sub: { color: '#666', marginBottom: 20 },
  card: {
    backgroundColor: '#FFF',
    padding: 20,
    borderRadius: 12,
    marginBottom: 16,
  },
  row: { fontSize: 16, marginBottom: 8, color: '#333' },
  hint: { color: '#999', fontStyle: 'italic' },
  note: { fontSize: 12, color: '#888', marginTop: 12 },
});

export default AdminDetailScreen;
