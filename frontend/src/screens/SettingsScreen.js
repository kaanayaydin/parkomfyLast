import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, TextInput, SafeAreaView } from 'react-native';

const SettingsScreen = ({ onLogout }) => {
  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Ayarlar</Text>
      
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Şifre Değiştir</Text>
        <TextInput style={styles.input} placeholder="Mevcut Şifre" secureTextEntry />
        <TextInput style={styles.input} placeholder="Yeni Şifre" secureTextEntry />
        <TouchableOpacity style={styles.saveButton}>
          <Text style={styles.saveText}>Şifreyi Güncelle</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.logoutButton} onPress={onLogout}>
        <Text style={styles.logoutText}>Çıkış Yap</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 20 },
  title: { fontSize: 28, fontWeight: 'bold', color: '#1A237E', marginVertical: 20 },
  section: { backgroundColor: '#FFF', padding: 20, borderRadius: 20, marginBottom: 20 },
  sectionTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 15, color: '#333' },
  input: { borderBottomWidth: 1, borderBottomColor: '#DDD', padding: 10, marginBottom: 10 },
  saveButton: { backgroundColor: '#B2FF59', padding: 10, borderRadius: 10, alignItems: 'center' },
  saveText: { color: '#1A237E', fontWeight: 'bold' },
  logoutButton: { backgroundColor: '#FF5252', padding: 15, borderRadius: 15, alignItems: 'center' },
  logoutText: { color: '#FFF', fontWeight: 'bold' }
});

export default SettingsScreen;