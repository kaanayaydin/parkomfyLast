import React, { useState, useEffect } from 'react';
import { View, Alert, StyleSheet, Text, TouchableOpacity } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import LoginScreen from './src/screens/LoginScreen';
import RegisterScreen from './src/screens/RegisterScreen';
import HomeScreen, { PARKING_LOTS } from './src/screens/HomeScreen';
import SlotSelectionScreen from './src/screens/SlotSelectionScreen';
import PaymentSummaryScreen from './src/screens/PaymentSummaryScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import ReservationsScreen from './src/screens/ReservationsScreen';
import EntranceScreen from './src/screens/EntranceScreen';
import {
  getLiveParkingStatus,
  getPublicParkingAreas,
  createReservation,
  getReservations,
  buildDateTime,
  registerUser,
  loginUser,
  mapUserFromApi,
  connectParkingWebSocket,
} from './src/config/api';
import { setupPushNotifications } from './src/config/notifications';

const TAB_ITEMS = [
  { id: 'Home', label: 'Otopark' },
  { id: 'Reservations', label: 'Rezervasyon' },
  { id: 'Profile', label: 'Profil' },
  { id: 'Settings', label: 'Ayarlar' },
];

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: 'row',
    alignItems: 'stretch',
    backgroundColor: '#FFF',
    borderTopWidth: 1,
    borderTopColor: '#EEE',
    paddingBottom: 8,
    minHeight: 76,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 14,
    paddingHorizontal: 4,
    minHeight: 64,
  },
  tabText: { color: '#999', fontWeight: '600', fontSize: 13, textAlign: 'center' },
  activeTabText: { color: '#1A237E', fontWeight: 'bold', fontSize: 14 },
});

export default function App() {
  const [currentScreen, setCurrentScreen] = useState('Login');
  const [activeTab, setActiveTab] = useState('Home');
  const [parkingData, setParkingData] = useState([]);
  const [selectedParkingId, setSelectedParkingId] = useState(null);
  const [paymentData, setPaymentData] = useState(null);
  const [reservations, setReservations] = useState([]);

  const [currentUser, setCurrentUser] = useState(null);

  const visibleTabs = TAB_ITEMS;

  const mapAreasToLots = (areas) => (areas || []).map((a) => ({
    id: a.areaId,
    areaId: a.areaId,
    name: a.areaName,
    location: a.address || '',
    price: 20,
    slots: Array.from({ length: a.slotCount || 0 }, (_, i) => ({ id: i + 1, status: 'available' })),
  }));

  const loadPublicAreas = async () => {
    try {
      const res = await getPublicParkingAreas();
      if (res.success && res.data?.length) {
        setParkingData(mapAreasToLots(res.data));
      } else {
        setParkingData(PARKING_LOTS.map((p) => ({ ...p, areaId: p.id })));
      }
    } catch {
      setParkingData(PARKING_LOTS.map((p) => ({ ...p, areaId: p.id })));
    }
  };

  const handleLogin = async (email, password) => {
    try {
      const res = await loginUser(email, password);
      if (res.success) {
        const user = mapUserFromApi(res.data);
        setCurrentUser(user);
        setActiveTab('Home');
        setCurrentScreen('MainApp');
        loadPublicAreas();
        setupPushNotifications(user.userId);
      } else {
        Alert.alert('Giriş başarısız', res.message || 'E-posta veya şifre hatalı');
      }
    } catch {
      Alert.alert('Bağlantı hatası', 'Sunucuya ulaşılamadı. Spring Boot çalışıyor mu?');
    }
  };

  const handleRegister = async ({ email, password, plate, fullName }) => {
    if (!fullName?.trim() || !email?.trim() || !plate?.trim() || !password) {
      Alert.alert('Eksik bilgi', 'Tüm alanları doldurun');
      return;
    }
    try {
      const res = await registerUser({
        email: email.trim(),
        password,
        fullName: fullName.trim(),
        licensePlate: plate.trim(),
      });
      if (res.success) {
        Alert.alert('Başarılı', 'Hesabınız veritabanına kaydedildi. Giriş yapabilirsiniz.');
        setCurrentScreen('Login');
      } else {
        Alert.alert('Kayıt başarısız', res.message || 'Kayıt oluşturulamadı');
      }
    } catch {
      Alert.alert('Bağlantı hatası', 'Sunucuya ulaşılamadı. Spring Boot çalışıyor mu?');
    }
  };

  useEffect(() => {
    if (currentScreen === 'Login' || currentScreen === 'Register') return;
    loadPublicAreas();
  }, [currentScreen]);

  useEffect(() => {
    if (currentScreen === 'Login' || currentScreen === 'Register') return;
    if (!currentUser?.plate) return;
    getReservations(currentUser.plate).then((res) => {
      if (res.success && res.data?.length) {
        setReservations(
          res.data.map((r) => ({
            ...r,
            parkingName: r.areaId,
            selectedSlot: r.slotId?.split('-').pop(),
            durationHours: Math.ceil(
              (new Date(r.endTime) - new Date(r.startTime)) / (1000 * 60 * 60)
            ),
          }))
        );
      }
    }).catch(() => {});
  }, [currentScreen, currentUser?.plate]);

  useEffect(() => {
    if (currentScreen === 'Login' || currentScreen === 'Register') return;

    const applyLiveData = (lotId, live) => {
      if (!live) return;
      setParkingData((prev) =>
        prev.map((park) => {
          if (park.id !== lotId) return park;
          const slotMap = {};
          (live.slots || []).forEach((s) => {
            const num = s.slotNumber || parseInt(s.slotId?.split('-').pop(), 10);
            if (num) slotMap[num] = s;
          });
          return {
            ...park,
            occupiedCount: live.occupiedSlots ?? 0,
            slots: park.slots.map((s) => {
              const liveSlot = slotMap[s.id];
              if (!liveSlot) return s;
              let status = 'available';
              if (liveSlot.mergedStatus === 'OCCUPIED') status = 'occupied';
              else if (liveSlot.mergedStatus === 'RESERVED') status = 'reserved';
              return {
                ...s,
                status,
                displayLabel: liveSlot.displayLabel,
                licensePlate: liveSlot.licensePlate,
              };
            }),
          };
        })
      );
    };

    const syncLive = async () => {
      try {
        const hour = new Date().getHours();
        const startTime = buildDateTime(0, hour);
        const endTime = buildDateTime(0, Math.min(22, hour + 2));
        const lots = parkingData.length ? parkingData : PARKING_LOTS.map((p) => ({ ...p, areaId: p.id }));
        const updates = await Promise.all(
          lots.map(async (lot) => {
            const areaId = lot.areaId || lot.id;
            const liveRes = await getLiveParkingStatus(areaId, startTime, endTime);
            return { lotId: lot.id, live: liveRes.success ? liveRes.data : null };
          })
        );
        updates.forEach((u) => applyLiveData(u.lotId, u.live));
      } catch (e) {
        console.log('Sync error (Spring Boot @8080):', e.message);
      }
    };

    syncLive();
    const syncInterval = setInterval(syncLive, 5000);
    const disconnectWs = connectParkingWebSocket((payload) => {
      if (payload.type === 'LIVE_STATUS' && payload.data?.areaId) {
        const lot = (parkingData.length ? parkingData : PARKING_LOTS).find(
          (p) => (p.areaId || p.id) === payload.data.areaId
        );
        if (lot) applyLiveData(lot.id, payload.data);
      }
    });

    return () => {
      clearInterval(syncInterval);
      disconnectWs();
    };
  }, [currentScreen, parkingData]);

  const handleNavigate = (screen, data = null) => {
    if (screen === 'Slots') setSelectedParkingId(data.id);
    if (screen === 'Payment') setPaymentData(data);
    setCurrentScreen(screen);
  };

  const finalizeBooking = async () => {
    try {
      const res = await createReservation({
        areaId: paymentData.areaId,
        slotId: paymentData.slotId,
        licensePlate: paymentData.licensePlate || currentUser?.plate,
        startTime: paymentData.startTime,
        endTime: paymentData.endTime,
      });
      if (!res.success) {
        Alert.alert('Hata', res.message || 'Rezervasyon oluşturulamadı');
        return;
      }
      const booking = {
        ...paymentData,
        ...res.data,
        totalFee: res.data.totalFee,
        date: new Date().toLocaleDateString(),
      };
      setReservations([booking, ...reservations]);
      Alert.alert('Başarılı', 'Slot seçilen saat aralığı için rezerve edildi!');
      setCurrentScreen('MainApp');
      setActiveTab('Reservations');
    } catch (e) {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı. Spring Boot çalışıyor mu?');
    }
  };

  const renderContent = () => {
    if (currentScreen === 'Entrance') {
      return <EntranceScreen onBack={() => setCurrentScreen('MainApp')} />;
    }
    if (activeTab === 'Profile') {
      return (
        <ProfileScreen
          user={currentUser}
          onEntrance={() => setCurrentScreen('Entrance')}
        />
      );
    }
    if (activeTab === 'Settings')
      return <SettingsScreen onLogout={() => { setCurrentUser(null); setCurrentScreen('Login'); }} />;
    if (activeTab === 'Reservations')
      return <ReservationsScreen reservations={reservations} />;
    if (currentScreen === 'Slots')
      return (
        <SlotSelectionScreen
          selectedParking={parkingData.find((p) => p.id === selectedParkingId)}
          areaId={parkingData.find((p) => p.id === selectedParkingId)?.areaId || selectedParkingId}
          licensePlate={currentUser?.plate}
          onNavigate={handleNavigate}
        />
      );
    if (currentScreen === 'Payment')
      return (
        <PaymentSummaryScreen
          paymentData={paymentData}
          onNavigate={finalizeBooking}
        />
      );
    return <HomeScreen onNavigate={handleNavigate} parkingData={parkingData} />;
  };

  return (
    <SafeAreaProvider>
      <SafeAreaView style={{ flex: 1, backgroundColor: '#FFF' }} edges={['top']}>
        {currentScreen === 'Login' ? (
          <LoginScreen
            onLogin={handleLogin}
            onNavigate={setCurrentScreen}
          />
        ) : currentScreen === 'Register' ? (
          <RegisterScreen
            onNavigate={setCurrentScreen}
            onRegister={handleRegister}
          />
        ) : (
          <View style={{ flex: 1 }}>
            <View style={{ flex: 1 }}>{renderContent()}</View>
            <View style={styles.tabBar}>
              {visibleTabs.map((tab) => (
                <TouchableOpacity
                  key={tab.id}
                  style={styles.tabItem}
                  activeOpacity={0.7}
                  hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}
                  onPress={() => {
                    setActiveTab(tab.id);
                    if (tab.id === 'Home') setCurrentScreen('MainApp');
                  }}
                >
                  <Text
                    style={[
                      styles.tabText,
                      activeTab === tab.id && styles.activeTabText,
                    ]}
                    numberOfLines={1}
                  >
                    {tab.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}
