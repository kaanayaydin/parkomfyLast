import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView,
  SafeAreaView, Alert, ActivityIndicator,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { detectPlateFromImage } from '../config/api';

const RegisterScreen = ({ onNavigate, onRegister }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [plate, setPlate] = useState('');
  const [fullName, setFullName] = useState('');
  const [scanning, setScanning] = useState(false);

  const scanPlate = async () => {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) {
      Alert.alert('İzin gerekli', 'Kamera izni verin');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
    });
    if (result.canceled || !result.assets?.[0]?.uri) return;
    setScanning(true);
    try {
      const res = await detectPlateFromImage(result.assets[0].uri);
      if (res.success && res.data?.licensePlateText) {
        setPlate(res.data.licensePlateText.replace(/\s+/g, ' ').trim());
        Alert.alert('Plaka okundu', res.data.licensePlateText);
      } else {
        Alert.alert('Okunamadı', res.message || 'Plaka tespit edilemedi');
      }
    } catch {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı');
    } finally {
      setScanning(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContainer}>
        <Text style={styles.title}>Yeni Hesap</Text>
        
        <View style={styles.form}>
          <TextInput 
            style={styles.input} 
            placeholder="Ad Soyad" 
            placeholderTextColor="#999"
            value={fullName}
            onChangeText={setFullName}
          />
          <TextInput 
            style={styles.input} 
            placeholder="E-posta" 
            placeholderTextColor="#999"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
          />
          <TextInput 
            style={styles.input} 
            placeholder="Plaka No (Örn: 34ABC123)" 
            placeholderTextColor="#999"
            value={plate}
            onChangeText={setPlate}
          />
          <TouchableOpacity style={styles.scanBtn} onPress={scanPlate} disabled={scanning}>
            {scanning ? (
              <ActivityIndicator color="#1A237E" />
            ) : (
              <Text style={styles.scanBtnText}>📷 Plakayı Kameradan Oku (OCR)</Text>
            )}
          </TouchableOpacity>
          <TextInput 
            style={styles.input} 
            placeholder="Şifre" 
            secureTextEntry 
            placeholderTextColor="#999"
            value={password}
            onChangeText={setPassword}
          />

          <TouchableOpacity 
            style={styles.registerButton} 
            onPress={() => onRegister({ email, password, plate, fullName })}
          >
            <Text style={styles.buttonText}>Hesap Oluştur</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.footerLink} onPress={() => onNavigate('Login')}>
            <Text style={styles.footerText}>Zaten üye misiniz? <Text style={styles.linkBold}>Giriş Yap</Text></Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  scrollContainer: { paddingVertical: 60 },
  title: { fontSize: 32, fontWeight: 'bold', color: '#1A237E', textAlign: 'center', marginBottom: 40 },
  form: { paddingHorizontal: 30 },
  input: { 
    backgroundColor: '#FFF', padding: 18, borderRadius: 15, 
    marginBottom: 12, borderWidth: 1, borderColor: '#DDD' 
  },
  registerButton: { 
    backgroundColor: '#1A237E', padding: 18, borderRadius: 15, 
    alignItems: 'center', marginTop: 20 
  },
  buttonText: { color: '#B2FF59', fontWeight: 'bold', fontSize: 18 },
  footerLink: { marginTop: 20, alignItems: 'center' },
  footerText: { color: '#666' },
  linkBold: { color: '#1A237E', fontWeight: 'bold' },
  scanBtn: {
    backgroundColor: '#E8EAF6',
    padding: 14,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: 8,
  },
  scanBtnText: { color: '#1A237E', fontWeight: '600' },
});

export default RegisterScreen;