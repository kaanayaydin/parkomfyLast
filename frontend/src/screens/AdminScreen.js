import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';

const ADMIN_OTOPARKLAR = [
  { id: 'istasyon1', ad: 'A Blok Otoparkı', lokasyon: 'Zemin Kat' },
  { id: 'istasyon2', ad: 'B Blok Otoparkı', lokasyon: 'Kat -1' },
  { id: 'istasyon3', ad: 'C Blok Otoparkı', lokasyon: 'Kat -2' },
];

const AdminScreen = ({ onEnterDetail }) => {
  return (
    <ScrollView style={styles.container}>
      <Text style={styles.header}>Yönetici Paneli</Text>
      {ADMIN_OTOPARKLAR.map((item) => (
        <TouchableOpacity 
          key={item.id} 
          style={styles.card} 
          onPress={() => onEnterDetail(item)}
        >
          <View>
            <Text style={styles.cardTitle}>{item.ad}</Text>
            <Text style={styles.cardSub}>{item.lokasyon}</Text>
          </View>
          <Text style={styles.arrow}>→</Text>
        </TouchableOpacity>
      ))}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F6FA', padding: 20 },
  header: { fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 },
  card: { backgroundColor: '#FFF', padding: 20, borderRadius: 15, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15, elevation: 3 },
  cardTitle: { fontSize: 18, fontWeight: 'bold' },
  cardSub: { fontSize: 12, color: '#888' },
  arrow: { fontSize: 20, fontWeight: 'bold', color: '#1A237E' }
});

// BURASI ÇOK ÖNEMLİ: export default olmalı
export default AdminScreen;