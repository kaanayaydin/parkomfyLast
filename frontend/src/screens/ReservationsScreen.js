import React, { useState } from 'react';
import {
  View, Text, StyleSheet, FlatList, SafeAreaView,
  TouchableOpacity, ActivityIndicator, Alert,
} from 'react-native';
import {
  formatDateTime,
  reservationStatusLabel,
  canCancelReservation,
} from '../config/api';

const ReservationsScreen = ({ reservations, onCancel }) => {
  const [cancellingId, setCancellingId] = useState(null);

  const handleCancel = (item) => {
    if (!canCancelReservation(item) || !onCancel) return;
    Alert.alert(
      'Rezervasyonu iptal et',
      `${item.parkingName || item.areaId} — Slot ${item.selectedSlot || item.slotId}\nBu rezervasyonu iptal etmek istiyor musunuz?`,
      [
        { text: 'Vazgeç', style: 'cancel' },
        {
          text: 'İptal et',
          style: 'destructive',
          onPress: async () => {
            setCancellingId(item.reservationId);
            try {
              await onCancel(item.reservationId);
            } finally {
              setCancellingId(null);
            }
          },
        },
      ]
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Rezervasyonlarım</Text>
      {reservations.length === 0 ? (
        <Text style={styles.emptyText}>Henüz bir rezervasyonunuz bulunmuyor.</Text>
      ) : (
        <FlatList
          data={reservations}
          keyExtractor={(item) => item.reservationId || `${item.areaId}-${item.startTime}`}
          renderItem={({ item }) => {
            const cancelled = item.status === 'CANCELLED';
            const cancellable = canCancelReservation(item);
            const statusLabel = reservationStatusLabel(item.status);
            const isCancelling = cancellingId === item.reservationId;

            return (
              <View style={[styles.resCard, cancelled && styles.resCardCancelled]}>
                <View style={styles.resHeader}>
                  <Text style={styles.resParkName}>{item.parkingName || item.areaId}</Text>
                  <Text style={[
                    styles.resStatus,
                    cancelled && styles.resStatusCancelled,
                    item.status === 'RESERVED' && styles.resStatusActive,
                  ]}>
                    {statusLabel}
                  </Text>
                </View>
                <View style={styles.divider} />
                <Text style={styles.resLabel}>Slot: {item.selectedSlot || item.slotId}</Text>
                <Text style={styles.resTime}>
                  {formatDateTime(item.startTime)} → {formatDateTime(item.endTime)}
                </Text>
                <View style={styles.resInfoRow}>
                  <Text style={styles.resLabel}>{item.durationHours || '-'} saat</Text>
                  <Text style={styles.resPrice}>{item.totalFee} TL</Text>
                </View>
                {cancellable && (
                  <TouchableOpacity
                    style={[styles.cancelBtn, isCancelling && styles.cancelBtnDisabled]}
                    onPress={() => handleCancel(item)}
                    disabled={isCancelling}
                  >
                    {isCancelling ? (
                      <ActivityIndicator color="#FFF" size="small" />
                    ) : (
                      <Text style={styles.cancelBtnText}>Rezervasyonu İptal Et</Text>
                    )}
                  </TouchableOpacity>
                )}
              </View>
            );
          }}
        />
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 20 },
  title: { fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 },
  emptyText: { textAlign: 'center', color: '#999', marginTop: 50 },
  resCard: { backgroundColor: '#FFF', padding: 20, borderRadius: 15, marginBottom: 15, elevation: 2 },
  resCardCancelled: { opacity: 0.65 },
  resHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  resParkName: { fontSize: 16, fontWeight: 'bold', color: '#1A237E', flex: 1, marginRight: 8 },
  resStatus: { color: '#4CAF50', fontWeight: 'bold', fontSize: 12 },
  resStatusActive: { color: '#1A237E' },
  resStatusCancelled: { color: '#999' },
  divider: { height: 1, backgroundColor: '#EEE', marginVertical: 10 },
  resInfoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 6 },
  resLabel: { color: '#666', fontSize: 13 },
  resTime: { color: '#444', fontSize: 12, marginTop: 4 },
  resPrice: { fontSize: 18, fontWeight: 'bold', color: '#1A237E' },
  cancelBtn: {
    marginTop: 14,
    backgroundColor: '#D32F2F',
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: 'center',
  },
  cancelBtnDisabled: { opacity: 0.7 },
  cancelBtnText: { color: '#FFF', fontWeight: 'bold', fontSize: 14 },
});

export default ReservationsScreen;
