import React from 'react';
import { View, Text, FlatList, TouchableOpacity, SafeAreaView } from 'react-native';

const HomeScreen = ({ onNavigate, parkingData }) => {
  const list = parkingData || [];

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: '#F5F5F5', paddingHorizontal: 20 }}>
      <Text style={{ fontSize: 24, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 }}>
        Otopark Seçiniz
      </Text>
      {!list.length ? (
        <Text style={{ fontSize: 15, color: '#666', lineHeight: 22 }}>
          Henüz kayıtlı otopark yok. Yönetici admin panelden otopark oluşturup kalibre etmeli.
        </Text>
      ) : null}
      <FlatList
        data={list}
        keyExtractor={(item) => item.id || item.areaId}
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