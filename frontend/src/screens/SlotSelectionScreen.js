import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, SafeAreaView } from 'react-native';

const SlotSelectionScreen = ({ onNavigate, selectedParking }) => {
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [hours, setHours] = useState(1);
  
  const unitPrice = selectedParking?.price || 20; 
  const totalFee = hours * unitPrice;

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>{selectedParking?.name}</Text>
      
      <Text style={styles.label}>1. Süre Seçin ({hours} Saat)</Text>
      <View style={styles.hourRow}>
        {[1, 2, 3, 5].map(h => (
          <TouchableOpacity key={h} style={[styles.hourBtn, hours === h && styles.activeHour]} onPress={() => setHours(h)}>
            <Text style={[styles.hourText, hours === h && styles.activeHourText]}>{h}s</Text>
          </TouchableOpacity>
        ))}
      </View>

      <Text style={styles.label}>2. Slot Seçin (Kırmızı: Dolu)</Text>
      <ScrollView contentContainerStyle={styles.grid}>
        {selectedParking?.slots?.map((slot) => (
          <TouchableOpacity 
            key={slot.id} 
            disabled={slot.status === 'occupied'}
            style={[
              styles.slot, 
              selectedSlot === slot.id && styles.activeSlot,
              slot.status === 'occupied' && styles.occSlot
            ]} 
            onPress={() => setSelectedSlot(slot.id)}
          >
            <Text style={[styles.slotText, slot.status === 'occupied' && styles.occText]}>{slot.id}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <View style={styles.footer}>
        <View><Text>Toplam:</Text><Text style={styles.total}>{totalFee} TL</Text></View>
        <TouchableOpacity 
          style={[styles.confirm, !selectedSlot && {opacity: 0.5}]}
          onPress={() => onNavigate('Payment', { 
            totalFee, 
            selectedSlot, 
            hours, 
            parkingName: selectedParking?.name 
          })}
          disabled={!selectedSlot}
        >
          <Text style={styles.confirmTxt}>Ödemeye Geç</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 20 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A237E', marginBottom: 20 },
  label: { fontSize: 14, fontWeight: 'bold', color: '#666', marginBottom: 10 },
  hourRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 25 },
  hourBtn: { padding: 12, borderRadius: 10, backgroundColor: '#FFF', width: '22%', alignItems: 'center', borderWidth: 1, borderColor: '#DDD' },
  activeHour: { backgroundColor: '#1A237E' },
  hourText: { fontWeight: 'bold' },
  activeHourText: { color: '#FFF' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  slot: { backgroundColor: '#FFF', padding: 20, borderRadius: 15, width: '46%', marginBottom: 15, alignItems: 'center', borderWidth: 1, borderColor: '#EEE' },
  activeSlot: { borderColor: '#B2FF59', borderWidth: 2 },
  occSlot: { backgroundColor: '#FFEBEE', borderColor: '#FFCDD2' },
  slotText: { fontWeight: 'bold', color: '#1A237E' },
  occText: { color: '#EF5350' },
  footer: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderTopWidth: 1, borderColor: '#EEE', paddingTop: 20 },
  total: { fontSize: 26, fontWeight: 'bold', color: '#1A237E' },
  confirm: { backgroundColor: '#1A237E', padding: 18, borderRadius: 15 },
  confirmTxt: { color: '#B2FF59', fontWeight: 'bold' }
});

export default SlotSelectionScreen; 