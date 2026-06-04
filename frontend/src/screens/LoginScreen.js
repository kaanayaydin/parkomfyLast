import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, SafeAreaView } from 'react-native';

const LoginScreen = ({ onNavigate, onLogin }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.logoText}>Parkomfy</Text>
        <Text style={styles.subtitle}>Konforlu otopark deneyimi.</Text>
      </View>

      <View style={styles.form}>
        <TextInput 
          style={styles.input} 
          placeholder="Kullanıcı Adı (admin)" 
          placeholderTextColor="#999"
          value={username}
          onChangeText={setUsername}
          autoCapitalize="none"
        />
        <TextInput 
          style={styles.input} 
          placeholder="Şifre" 
          secureTextEntry 
          placeholderTextColor="#999"
          value={password}
          onChangeText={setPassword}
        />
        
        <TouchableOpacity style={styles.loginButton} onPress={() => onLogin(username, password)}>
          <Text style={styles.buttonText}>Giriş Yap</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.footerLink} onPress={() => onNavigate('Register')}>
          <Text style={styles.footerText}>Hesabınız yok mu? <Text style={styles.linkBold}>Kayıt Ol</Text></Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', justifyContent: 'center' },
  header: { alignItems: 'center', marginBottom: 50 },
  logoText: { fontSize: 42, fontWeight: 'bold', color: '#1A237E' },
  subtitle: { fontSize: 16, color: '#666' },
  form: { paddingHorizontal: 30 },
  input: { backgroundColor: '#FFF', padding: 18, borderRadius: 15, marginBottom: 15, borderWidth: 1, borderColor: '#DDD' },
  loginButton: { backgroundColor: '#B2FF59', padding: 18, borderRadius: 15, alignItems: 'center' },
  buttonText: { color: '#1A237E', fontWeight: 'bold', fontSize: 18 },
  footerLink: { marginTop: 25, alignItems: 'center' },
  footerText: { color: '#666' },
  linkBold: { color: '#1A237E', fontWeight: 'bold' }
});

export default LoginScreen;