import React from 'react';
import { View, Text, FlatList, TouchableOpacity, SafeAreaView } from 'react-native';

// Backend ile %100 uyumlu otopark yapısı
export const PARKING_LOTS = [
  { 
    id: 'istasyon1', name: 'A Blok Otoparkı', location: 'Zemin Kat', price: 20,
    slots: Array.from({ length: 5 }, (_, i) => ({ id: i + 1, status: 'available' }))
  },
  { 
    id: 'istasyon2', name: 'B Blok Otoparkı', location: 'Kat -1', price: 35,
    slots: Array.from({ length: 4 }, (_, i) => ({ id: i + 1, status: 'available' }))
  },
  { 
    id: 'istasyon3', name: 'C Blok Otoparkı', location: 'Kat -2', price: 15,
    slots: Array.from({ length: 3 }, (_, i) => ({ id: i + 1, status: 'available' }))
  },
];

const HomeScreen = ({ onNavigate, parkingData }) => {
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: '#F5F5F5', paddingHorizontal: 20 }}>
      <Text style={{ fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 }}>
        Otopark Seçiniz
      </Text>
      <FlatList
        data={parkingData} // App.js'den gelen canlı veriyi kullanır
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <TouchableOpacity 
            style={{ 
              backgroundColor: '#FFF', padding: 20, borderRadius: 15, 
              flexDirection: 'row', justifyContent: 'space-between', 
              alignItems: 'center', marginBottom: 15, elevation: 3 
            }} 
            onPress={() => onNavigate('Slots', item)}
          >
            <View>
              <Text style={{ fontSize: 18, fontWeight: 'bold', color: '#333' }}>{item.name}</Text>
              <Text style={{ fontSize: 14, color: '#666', marginTop: 4 }}>{item.location}</Text>
            </View>
            <View style={{ backgroundColor: '#B2FF59', padding: 8, borderRadius: 10 }}>
              <Text style={{ fontSize: 14, fontWeight: 'bold', color: '#1A237E' }}>
                {item.price} TL/s
              </Text>
            </View>
          </TouchableOpacity>
        )}
      />
    </SafeAreaView>
  );
};

export default HomeScreen;