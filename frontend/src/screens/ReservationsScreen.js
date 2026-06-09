import React from 'react';
import { View, Text, StyleSheet, FlatList, SafeAreaView } from 'react-native';
import { formatDateTime } from '../config/api';

const ReservationsScreen = ({ reservations }) => {
  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Rezervasyonlarım</Text>
      {reservations.length === 0 ? (
        <Text style={styles.emptyText}>Henüz bir rezervasyonunuz bulunmuyor.</Text>
      ) : (
        <FlatList
          data={reservations}
          keyExtractor={(item, index) => index.toString()}
          renderItem={({ item }) => (
            <View style={styles.resCard}>
              <View style={styles.resHeader}>
                <Text style={styles.resParkName}>{item.parkingName}</Text>
                <Text style={styles.resStatus}>Tamamlandı</Text>
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
            </View>
          )}
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
  resHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  resParkName: { fontSize: 16, fontWeight: 'bold', color: '#1A237E' },
  resStatus: { color: '#4CAF50', fontWeight: 'bold', fontSize: 12 },
  divider: { height: 1, backgroundColor: '#EEE', marginVertical: 10 },
  resInfoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 6 },
  resLabel: { color: '#666', fontSize: 13 },
  resTime: { color: '#444', fontSize: 12, marginTop: 4 },
  resPrice: { fontSize: 18, fontWeight: 'bold', color: '#1A237E' }
});

export default ReservationsScreen;