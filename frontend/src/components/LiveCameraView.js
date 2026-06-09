import React, { useState, useEffect, useRef } from 'react';
import { View, Image, Text, StyleSheet, ActivityIndicator } from 'react-native';
import { getLiveCameraSnapshotUrl, getLiveCameraStatus } from '../config/api';

/**
 * loop1.mp4 simülasyonu — sunucudan ~8 fps snapshot polling ile canlı görüntü.
 */
const LiveCameraView = ({ height = 220, label = 'Canlı Kamera (loop1.mp4)' }) => {
  const [frameKey, setFrameKey] = useState(0);
  const [available, setAvailable] = useState(null);
  const intervalRef = useRef(null);

  useEffect(() => {
    getLiveCameraStatus().then((s) => setAvailable(s.available)).catch(() => setAvailable(false));
    intervalRef.current = setInterval(() => setFrameKey((k) => k + 1), 120);
    return () => clearInterval(intervalRef.current);
  }, []);

  if (available === false) {
    return (
      <View style={[styles.box, { height }]}>
        <Text style={styles.off}>Kamera simülasyonu kapalı</Text>
        <Text style={styles.hint}>grpc_server/server.py çalıştırın (loop1.mp4)</Text>
      </View>
    );
  }

  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <View style={[styles.box, { height }]}>
        {available === null ? (
          <ActivityIndicator color="#1A237E" />
        ) : (
          <Image
            source={{ uri: getLiveCameraSnapshotUrl(frameKey) }}
            style={{ width: '100%', height: '100%' }}
            resizeMode="cover"
          />
        )}
        <View style={styles.liveBadge}>
          <View style={styles.dot} />
          <Text style={styles.liveText}>CANLI</Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  box: {
    backgroundColor: '#111',
    borderRadius: 10,
    overflow: 'hidden',
    justifyContent: 'center',
    alignItems: 'center',
  },
  label: { fontWeight: '600', color: '#1A237E', marginBottom: 8 },
  off: { color: '#C62828', fontWeight: 'bold' },
  hint: { color: '#888', fontSize: 12, marginTop: 6, textAlign: 'center', paddingHorizontal: 12 },
  liveBadge: {
    position: 'absolute',
    top: 8,
    left: 8,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.55)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#F44336', marginRight: 6 },
  liveText: { color: '#FFF', fontSize: 11, fontWeight: 'bold' },
});

export default LiveCameraView;
