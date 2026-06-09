import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, SafeAreaView, Alert, ActivityIndicator,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { processEntrancePlate } from '../config/api';

const EntranceScreen = ({ onBack }) => {
  const [loading, setLoading] = useState(false);
  const [lastResult, setLastResult] = useState(null);

  const handleScan = async () => {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) {
      Alert.alert('İzin gerekli', 'Kamera izni verin');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
    });
    if (result.canceled || !result.assets?.[0]?.uri) return;

    setLoading(true);
    try {
      const res = await processEntrancePlate(result.assets[0].uri);
      if (res.success) {
        setLastResult(res.data);
        const msg = res.data.hasReservation
          ? `Plaka kaydedildi. Rezervasyonlu slot: ${res.data.reservedSlotId}`
          : `Plaka veritabanına kaydedildi: ${res.data.licensePlate}`;
        Alert.alert('Giriş OK', msg);
      } else {
        Alert.alert('Hata', res.message || 'Plaka okunamadı');
      }
    } catch {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <TouchableOpacity onPress={onBack}>
        <Text style={styles.back}>← Geri</Text>
      </TouchableOpacity>
      <Text style={styles.title}>Giriş Plaka Okuma</Text>
      <Text style={styles.sub}>
        Giriş kamerası plakayı okur, veritabanına kaydeder ve aktif rezervasyonu eşleştirir.
      </Text>

      <TouchableOpacity style={styles.btn} onPress={handleScan} disabled={loading}>
        {loading ? (
          <ActivityIndicator color="#FFF" />
        ) : (
          <Text style={styles.btnText}>📷 Giriş Kamerası ile Tara</Text>
        )}
      </TouchableOpacity>

      {lastResult && (
        <View style={styles.card}>
          <Text style={styles.row}>Plaka: {lastResult.licensePlate}</Text>
          <Text style={styles.row}>Güven: {(lastResult.confidence * 100).toFixed(0)}%</Text>
          {lastResult.hasReservation && (
            <Text style={styles.row}>Rezervasyon: {lastResult.reservedSlotId}</Text>
          )}
        </View>
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, backgroundColor: '#F5F5F5' },
  back: { color: '#1A237E', fontWeight: 'bold', marginBottom: 16 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A237E' },
  sub: { color: '#666', marginVertical: 16, lineHeight: 22 },
  btn: {
    backgroundColor: '#1A237E',
    padding: 18,
    borderRadius: 12,
    alignItems: 'center',
  },
  btnText: { color: '#FFF', fontWeight: 'bold', fontSize: 16 },
  card: {
    marginTop: 24,
    backgroundColor: '#FFF',
    padding: 16,
    borderRadius: 12,
  },
  row: { fontSize: 15, marginBottom: 6, color: '#333' },
});

export default EntranceScreen;
