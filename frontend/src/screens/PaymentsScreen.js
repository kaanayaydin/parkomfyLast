import React, { useCallback, useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList, SafeAreaView,
  ActivityIndicator, RefreshControl, TouchableOpacity,
} from 'react-native';
import {
  formatDateTime,
  formatDurationSeconds,
  paymentStatusLabel,
  getMyPayments,
} from '../config/api';

function PaymentCard({ item, onPay }) {
  const slotLabel = item.slotId ? item.slotId.split('-').pop() : '-';
  const canPay = item.status === 'PENDING' && item.amount > 0;
  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <Text style={styles.parkName}>{item.areaName || item.areaId || 'Otopark'}</Text>
        <Text style={[
          styles.status,
          item.status === 'PENDING' && styles.statusPending,
          item.status === 'COMPLETED' && styles.statusPaid,
        ]}>
          {paymentStatusLabel(item.status)}
        </Text>
      </View>
      <View style={styles.divider} />
      <Text style={styles.row}>Slot: {slotLabel}</Text>
      <Text style={styles.row}>
        Giriş: {formatDateTime(item.entryTime)}
      </Text>
      <Text style={styles.row}>
        Çıkış: {formatDateTime(item.exitTime)}
      </Text>
      <View style={styles.footer}>
        <Text style={styles.duration}>
          Süre: {formatDurationSeconds(item.durationSeconds)}
        </Text>
        <Text style={styles.amount}>{item.amount} TL</Text>
      </View>
      {canPay && (
        <TouchableOpacity style={styles.payBtn} onPress={() => onPay(item)}>
          <Text style={styles.payBtnText}>Öde</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const PaymentsScreen = ({ licensePlate, onPay }) => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async (silent = false) => {
    if (!licensePlate) {
      setItems([]);
      setLoading(false);
      return;
    }
    if (!silent) setLoading(true);
    try {
      const res = await getMyPayments(licensePlate);
      if (res.success) {
        setItems(res.data || []);
        setError(null);
      } else {
        setItems([]);
        if (!silent) setError(res.message || 'Ödemeler yüklenemedi');
      }
    } catch (e) {
      if (!silent) {
        setItems([]);
        setError(e.message || 'Bağlantı hatası');
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [licensePlate]);

  useEffect(() => {
    load();
    const timer = setInterval(() => load(true), 8000);
    return () => clearInterval(timer);
  }, [load]);

  const onRefresh = () => {
    setRefreshing(true);
    load(true);
  };

  if (loading && !items.length) {
    return (
      <SafeAreaView style={styles.center}>
        <ActivityIndicator size="large" color="#1A237E" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Ödemelerim</Text>
      <Text style={styles.subtitle}>
        Giriş-çıkış park ücretleri plakanıza ({licensePlate || '-'}) göre listelenir.
      </Text>
      {error ? (
        <Text style={styles.errorBanner}>{error}</Text>
      ) : null}
      {!items.length && !error ? (
        <Text style={styles.empty}>
          Henüz ödeme kaydı yok. Otoparka girip çıktığınızda ücret burada görünür.
        </Text>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.paymentId}
          renderItem={({ item }) => <PaymentCard item={item} onPay={onPay} />}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
          contentContainerStyle={{ paddingBottom: 24 }}
        />
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', paddingHorizontal: 20 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  title: { fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginVertical: 16 },
  subtitle: { fontSize: 13, color: '#666', marginBottom: 12, lineHeight: 18 },
  errorBanner: {
    backgroundColor: '#FFEBEE',
    color: '#C62828',
    padding: 12,
    borderRadius: 10,
    marginBottom: 12,
    fontSize: 14,
    lineHeight: 20,
  },
  empty: { fontSize: 15, color: '#666', lineHeight: 22, marginTop: 8 },
  card: {
    backgroundColor: '#FFF',
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
  },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  parkName: { fontSize: 17, fontWeight: 'bold', color: '#333', flex: 1 },
  status: { fontSize: 12, fontWeight: '700', color: '#666', marginLeft: 8 },
  statusPending: { color: '#E65100' },
  statusPaid: { color: '#2E7D32' },
  divider: { height: 1, backgroundColor: '#EEE', marginVertical: 10 },
  row: { fontSize: 14, color: '#555', marginBottom: 4 },
  footer: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8, alignItems: 'center' },
  duration: { fontSize: 15, fontWeight: '600', color: '#1A237E' },
  amount: { fontSize: 18, fontWeight: 'bold', color: '#333' },
  payBtn: {
    marginTop: 12,
    backgroundColor: '#B2FF59',
    paddingVertical: 12,
    borderRadius: 12,
    alignItems: 'center',
  },
  payBtnText: { color: '#1A237E', fontWeight: 'bold', fontSize: 16 },
});

export default PaymentsScreen;
