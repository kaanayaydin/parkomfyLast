import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, SafeAreaView } from 'react-native';
import { formatDateTime, formatDurationSeconds } from '../config/api';

const PaymentSummaryScreen = ({ onNavigate, paymentData, isWalkIn, onBack }) => {
  const durationLabel = isWalkIn
    ? formatDurationSeconds(paymentData?.durationSeconds)
    : `${paymentData?.durationHours || 1} Saat`;
  const totalFee = paymentData?.totalFee ?? 0;
  const feeDisplay = Number.isInteger(totalFee) ? `${totalFee},00` : totalFee.toFixed(2).replace('.', ',');

  return (
    <SafeAreaView style={styles.container}>
      {onBack && (
        <TouchableOpacity style={styles.backBtn} onPress={onBack}>
          <Text style={styles.backText}>← Geri</Text>
        </TouchableOpacity>
      )}
      <View style={styles.receiptCard}>
        <Text style={styles.brand}>{isWalkIn ? 'Park Ücreti' : 'Parkomfy Ödeme'}</Text>
        <View style={styles.divider} />
        
        <View style={styles.row}>
          <Text style={styles.label}>Otopark:</Text>
          <Text style={styles.value}>{paymentData?.parkingName || '-'}</Text>
        </View>

        <View style={styles.row}>
          <Text style={styles.label}>{isWalkIn ? 'Slot:' : 'Seçilen Slot:'}</Text>
          <Text style={styles.value}>{paymentData?.selectedSlot || '-'}</Text>
        </View>

        <View style={styles.row}>
          <Text style={styles.label}>{isWalkIn ? 'Giriş:' : 'Başlangıç:'}</Text>
          <Text style={styles.value}>{formatDateTime(paymentData?.startTime)}</Text>
        </View>

        <View style={styles.row}>
          <Text style={styles.label}>{isWalkIn ? 'Çıkış:' : 'Bitiş:'}</Text>
          <Text style={styles.value}>{formatDateTime(paymentData?.endTime)}</Text>
        </View>

        <View style={styles.row}>
          <Text style={styles.label}>Süre:</Text>
          <Text style={styles.value}>{durationLabel}</Text>
        </View>

        <View style={styles.divider} />
        
        <View style={styles.totalRow}>
          <Text style={styles.totalLabel}>Toplam:</Text>
          <Text style={styles.totalValue}>{feeDisplay} TL</Text>
        </View>
      </View>

      <TouchableOpacity style={styles.payButton} onPress={onNavigate}>
        <Text style={styles.payText}>Stripe ile Öde</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8F9FA', padding: 20, justifyContent: 'center' },
  backBtn: { alignSelf: 'flex-start', marginBottom: 12 },
  backText: { color: '#1A237E', fontSize: 16, fontWeight: '600' },
  receiptCard: { backgroundColor: '#FFF', padding: 30, borderRadius: 30, elevation: 5 },
  brand: { fontSize: 24, fontWeight: 'bold', color: '#1A237E', textAlign: 'center', marginBottom: 20 },
  divider: { height: 1, backgroundColor: '#EEE', marginVertical: 20 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 15 },
  label: { color: '#666', fontSize: 16 },
  value: { color: '#1A237E', fontSize: 16, fontWeight: 'bold' },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  totalLabel: { fontSize: 18, fontWeight: 'bold' },
  totalValue: { fontSize: 32, fontWeight: 'bold', color: '#1A237E' },
  payButton: { backgroundColor: '#B2FF59', padding: 20, borderRadius: 20, alignItems: 'center', marginTop: 30 },
  payText: { color: '#1A237E', fontWeight: 'bold', fontSize: 18 }
});

export default PaymentSummaryScreen;