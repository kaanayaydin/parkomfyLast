import React, { useState, useEffect, useRef } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, Image } from 'react-native';
import { getLiveCameraSnapshotUrl, getLiveCameraStatus, LIVE_CAMERA_POLL_MS } from '../config/api';

/**
 * Canlı kamera: sunucudan snapshot polling.
 * Cift-tamponlama (double buffer): alttaki katman son yuklenen kareyi gosterir,
 * ustteki katman yeni kareyi arka planda yukler; yuklenince alta terfi eder.
 * Boylece kareler arasi siyah titreme/takilma olmaz. Polling zincirleme:
 * bir sonraki istek, onceki kare yuklendikten intervalMs sonra atilir -> istek
 * yigilmaz, cihaz hizina gore akar.
 */
const LiveCameraView = ({ height = 220, label, lotKey = 'loop1', intervalMs = LIVE_CAMERA_POLL_MS }) => {
  const displayLabel = label || `Canlı Kamera (${lotKey})`;
  const [baseUri, setBaseUri] = useState(null); // ekranda kalan son kare
  const [topUri, setTopUri] = useState(null); // arka planda yuklenen yeni kare
  const [loaded, setLoaded] = useState(false);
  const [available, setAvailable] = useState(null);
  const [errorCount, setErrorCount] = useState(0);
  const aliveRef = useRef(true);
  const timerRef = useRef(null);

  useEffect(() => {
    aliveRef.current = true;
    setLoaded(false);
    setErrorCount(0);
    setBaseUri(null);
    getLiveCameraStatus()
      .then((s) => aliveRef.current && setAvailable(s.available))
      .catch(() => aliveRef.current && setAvailable(false));
    setTopUri(getLiveCameraSnapshotUrl(lotKey, Date.now()));
    return () => {
      aliveRef.current = false;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [lotKey]);

  const scheduleNext = (delay) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      if (aliveRef.current) setTopUri(getLiveCameraSnapshotUrl(lotKey, Date.now()));
    }, delay);
  };

  const handleTopLoad = () => {
    if (!aliveRef.current) return;
    setBaseUri(topUri); // yuklenen kareyi gorunur katmana terfi et
    if (!loaded) setLoaded(true);
    if (errorCount) setErrorCount(0);
    scheduleNext(intervalMs);
  };

  const handleTopError = () => {
    if (!aliveRef.current) return;
    setErrorCount((c) => c + 1);
    scheduleNext(Math.max(intervalMs, 500));
  };

  if (available === false) {
    return (
      <View style={[styles.box, { height }]}>
        <Text style={styles.off}>Kamera simülasyonu kapalı</Text>
        <Text style={styles.hint}>grpc_server/server.py çalıştırın</Text>
      </View>
    );
  }

  return (
    <View>
      <Text style={styles.label}>{displayLabel}</Text>
      <View style={[styles.box, { height }]}>
        {baseUri ? (
          <Image
            source={{ uri: baseUri }}
            style={StyleSheet.absoluteFillObject}
            resizeMode="cover"
            fadeDuration={0}
          />
        ) : null}
        {topUri ? (
          <Image
            source={{ uri: topUri }}
            style={StyleSheet.absoluteFillObject}
            resizeMode="cover"
            fadeDuration={0}
            onLoad={handleTopLoad}
            onError={handleTopError}
          />
        ) : null}
        {!loaded ? <ActivityIndicator color="#1A237E" /> : null}
        {!loaded && errorCount > 0 ? (
          <Text style={styles.errorHint}>Görüntü yüklenemiyor ({errorCount})</Text>
        ) : null}
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
  errorHint: {
    position: 'absolute',
    bottom: 12,
    color: '#FFF',
    backgroundColor: 'rgba(198,40,40,0.8)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 6,
    fontSize: 12,
  },
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
