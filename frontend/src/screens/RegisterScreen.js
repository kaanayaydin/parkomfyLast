import React, { useEffect, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView,
  SafeAreaView, Alert, ActivityIndicator, Modal,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { detectPlateFromImage, getKvkkText } from '../config/api';

const RegisterScreen = ({ onNavigate, onRegister }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [plate, setPlate] = useState('');
  const [fullName, setFullName] = useState('');
  const [scanning, setScanning] = useState(false);
  const [kvkkAccepted, setKvkkAccepted] = useState(false);
  const [kvkkModalVisible, setKvkkModalVisible] = useState(false);
  const [kvkkText, setKvkkText] = useState('Yükleniyor...');

  useEffect(() => {
    (async () => {
      try {
        const res = await getKvkkText();
        if (res.success && res.data?.text) {
          setKvkkText(res.data.text);
        }
      } catch {
        setKvkkText('KVKK metni yüklenemedi. Lütfen daha sonra tekrar deneyin.');
      }
    })();
  }, []);

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

          <View style={styles.kvkkRow}>
            <TouchableOpacity
              style={[styles.checkbox, kvkkAccepted && styles.checkboxChecked]}
              onPress={() => setKvkkAccepted((v) => !v)}
            >
              {kvkkAccepted ? <Text style={styles.checkMark}>✓</Text> : null}
            </TouchableOpacity>
            <Text style={styles.kvkkLabel}>
              <Text onPress={() => setKvkkModalVisible(true)} style={styles.kvkkLink}>
                KVKK Aydınlatma Metni
              </Text>
              {' '}ni okudum ve onaylıyorum.
            </Text>
          </View>

          <TouchableOpacity
            style={[styles.registerButton, !kvkkAccepted && styles.registerButtonDisabled]}
            onPress={() => onRegister({ email, password, plate, fullName, kvkkAccepted })}
          >
            <Text style={styles.buttonText}>Hesap Oluştur</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.footerLink} onPress={() => onNavigate('Login')}>
            <Text style={styles.footerText}>
              Zaten üye misiniz? <Text style={styles.linkBold}>Giriş Yap</Text>
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>

      <Modal visible={kvkkModalVisible} animationType="slide" transparent>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>KVKK Aydınlatma Metni</Text>
            <ScrollView style={styles.modalScroll}>
              <Text style={styles.modalText}>{kvkkText}</Text>
            </ScrollView>
            <TouchableOpacity
              style={styles.modalCloseBtn}
              onPress={() => setKvkkModalVisible(false)}
            >
              <Text style={styles.modalCloseText}>Kapat</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
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
    marginBottom: 12, borderWidth: 1, borderColor: '#DDD',
  },
  registerButton: {
    backgroundColor: '#1A237E', padding: 18, borderRadius: 15,
    alignItems: 'center', marginTop: 20,
  },
  registerButtonDisabled: { opacity: 0.5 },
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
  kvkkRow: { flexDirection: 'row', alignItems: 'flex-start', marginTop: 8, marginBottom: 4 },
  checkbox: {
    width: 22, height: 22, borderWidth: 2, borderColor: '#1A237E',
    borderRadius: 4, marginRight: 10, alignItems: 'center', justifyContent: 'center',
  },
  checkboxChecked: { backgroundColor: '#1A237E' },
  checkMark: { color: '#FFF', fontWeight: 'bold', fontSize: 14 },
  kvkkLabel: { flex: 1, color: '#444', fontSize: 14, lineHeight: 20 },
  kvkkLink: { color: '#1A237E', fontWeight: 'bold', textDecorationLine: 'underline' },
  modalOverlay: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 20,
  },
  modalCard: {
    backgroundColor: '#FFF', borderRadius: 16, padding: 20, maxHeight: '80%',
  },
  modalTitle: { fontSize: 18, fontWeight: 'bold', color: '#1A237E', marginBottom: 12 },
  modalScroll: { marginBottom: 16 },
  modalText: { color: '#333', lineHeight: 22, fontSize: 14 },
  modalCloseBtn: {
    backgroundColor: '#1A237E', padding: 14, borderRadius: 12, alignItems: 'center',
  },
  modalCloseText: { color: '#B2FF59', fontWeight: 'bold' },
});

export default RegisterScreen;
