import React, { useMemo, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList, SafeAreaView,
  TouchableOpacity, ActivityIndicator, Alert,
} from 'react-native';
import {
  formatDateTime,
  reservationStatusLabel,
  canCancelReservation,
  groupReservations,
} from '../config/api';

const TABS = [
  { id: 'active', label: 'Aktif' },
  { id: 'past', label: 'Geçmiş' },
  { id: 'cancelled', label: 'İptal' },
];

function ReservationCard({ item, onCancel, cancellingId }) {
  const cancelled = item.status === 'CANCELLED';
  const cancellable = canCancelReservation(item);
  const statusLabel = reservationStatusLabel(item.status);
  const isCancelling = cancellingId === item.reservationId;

  const handleCancel = () => {
    if (!cancellable || !onCancel) return;
    Alert.alert(
      'Rezervasyonu iptal et',
      `${item.parkingName || item.areaId} — Slot ${item.selectedSlot || item.slotId}\nBu rezervasyonu iptal etmek istiyor musunuz?`,
      [
        { text: 'Vazgeç', style: 'cancel' },
        {
          text: 'İptal et',
          style: 'destructive',
          onPress: async () => {
            await onCancel(item.reservationId);
          },
        },
      ]
    );
  };

  return (
    <View style={[styles.resCard, cancelled && styles.resCardCancelled]}>
      <View style={styles.resHeader}>
        <Text style={styles.resParkName}>{item.parkingName || item.areaId}</Text>
        <Text style={[
          styles.resStatus,
          cancelled && styles.resStatusCancelled,
          (item.status === 'RESERVED' || item.status === 'ACTIVE') && styles.resStatusActive,
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
          onPress={handleCancel}
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
}

const ReservationsScreen = ({ reservations, onCancel }) => {
  const [activeTab, setActiveTab] = useState('active');
  const [cancellingId, setCancellingId] = useState(null);

  const groups = useMemo(() => groupReservations(reservations), [reservations]);
  const list = groups[activeTab] || [];

  const emptyMessages = {
    active: 'Aktif rezervasyonunuz yok.',
    past: 'Geçmiş rezervasyon bulunmuyor.',
    cancelled: 'İptal edilmiş rezervasyon yok.',
  };

  const handleCancel = async (reservationId) => {
    setCancellingId(reservationId);
    try {
      await onCancel(reservationId);
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Rezervasyonlarım</Text>

      <View style={styles.tabRow}>
        {TABS.map((tab) => {
          const count = (groups[tab.id] || []).length;
          const selected = activeTab === tab.id;
          return (
            <TouchableOpacity
              key={tab.id}
              style={[styles.tab, selected && styles.tabActive]}
              onPress={() => setActiveTab(tab.id)}
            >
              <Text style={[styles.tabText, selected && styles.tabTextActive]}>
                {tab.label}
              </Text>
              {count > 0 && (
                <View style={[styles.badge, selected && styles.badgeActive]}>
                  <Text style={[styles.badgeText, selected && styles.badgeTextActive]}>
                    {count}
                  </Text>
                </View>
              )}
            </TouchableOpacity>
          );
        })}
      </View>

      {list.length === 0 ? (
        <Text style={styles.emptyText}>{emptyMessages[activeTab]}</Text>
      ) : (
        <FlatList
          data={list}
          keyExtractor={(item) => item.reservationId || `${item.areaId}-${item.startTime}`}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => (
            <ReservationCard
              item={item}
              onCancel={handleCancel}
              cancellingId={cancellingId}
            />
          )}
        />
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', paddingHorizontal: 20 },
  title: { fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 },
  tabRow: {
    flexDirection: 'row',
    marginBottom: 16,
    backgroundColor: '#E8EAF6',
    borderRadius: 12,
    padding: 4,
  },
  tab: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 10,
    gap: 6,
  },
  tabActive: { backgroundColor: '#1A237E' },
  tabText: { fontSize: 13, fontWeight: '600', color: '#1A237E' },
  tabTextActive: { color: '#FFF' },
  badge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: '#C5CAE9',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 5,
  },
  badgeActive: { backgroundColor: 'rgba(255,255,255,0.25)' },
  badgeText: { fontSize: 11, fontWeight: 'bold', color: '#1A237E' },
  badgeTextActive: { color: '#FFF' },
  listContent: { paddingBottom: 24 },
  emptyText: { textAlign: 'center', color: '#999', marginTop: 40, fontSize: 15 },
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
