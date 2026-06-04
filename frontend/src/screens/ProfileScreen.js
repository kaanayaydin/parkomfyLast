import React from 'react';
import { View, Text, StyleSheet, SafeAreaView } from 'react-native';

const ProfileScreen = ({ user }) => {
  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Profil Bilgileri</Text>
      <View style={styles.infoCard}>
        <View style={styles.row}>
          <Text style={styles.label}>Ad Soyad:</Text>
          <Text style={styles.value}>{user?.fullName || "Kullanıcı"}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>E-posta:</Text>
          <Text style={styles.value}>{user?.email}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>Tanımlı Plaka:</Text>
          <Text style={styles.value}>{user?.plate}</Text>
        </View>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 20 },
  title: { fontSize: 28, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 },
  infoCard: { backgroundColor: '#FFF', padding: 20, borderRadius: 20, elevation: 3 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 15 },
  label: { color: '#666', fontSize: 16 },
  value: { color: '#1A237E', fontSize: 16, fontWeight: 'bold' }
});

export default ProfileScreen;