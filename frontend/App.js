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
import AdminScreen from './src/screens/AdminScreen';
import AdminDetailScreen from './src/screens/AdminDetailScreen';
import { AREA_IDS, getParkingStatus, getAvailableSlots } from './src/config/api';

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 15,
    backgroundColor: '#FFF',
    borderTopWidth: 1,
    borderTopColor: '#EEE',
    paddingBottom: 25,
  },
  tabText: { color: '#999', fontWeight: 'bold', fontSize: 10 },
  activeTabText: { color: '#1A237E' },
});

export default function App() {
  const [currentScreen, setCurrentScreen] = useState('Login');
  const [activeTab, setActiveTab] = useState('Home');
  const [parkingData, setParkingData] = useState(PARKING_LOTS);
  const [selectedParkingId, setSelectedParkingId] = useState(null);
  const [paymentData, setPaymentData] = useState(null);
  const [reservations, setReservations] = useState([]);
  const [adminSelectedPark, setAdminSelectedPark] = useState(null);

  const [registeredUser] = useState({
    email: 'admin',
    password: '1234',
    plate: '34 OZU 450',
    fullName: 'Yusuf Eren Erişmiş',
  });

  useEffect(() => {
    if (currentScreen === 'Login' || currentScreen === 'Register') return;

    const syncInterval = setInterval(async () => {
      try {
        const lotIds = ['istasyon1', 'istasyon2', 'istasyon3'];
        const updates = await Promise.all(
          lotIds.map(async (lotId) => {
            const areaId = AREA_IDS[lotId];
            const [statusRes, availRes] = await Promise.all([
              getParkingStatus(areaId),
              getAvailableSlots(areaId),
            ]);
            const availableIds = new Set(
              (availRes.data || []).map((s) => s.slotId)
            );
            return { lotId, statusRes, availableIds };
          })
        );

        setParkingData((prev) =>
          prev.map((park) => {
            const u = updates.find((x) => x.lotId === park.id);
            if (!u) return park;
            return {
              ...park,
              occupiedCount: u.statusRes.data?.occupiedSlots ?? 0,
              slots: park.slots.map((s) => {
                if (s.status === 'reserved') return s;
                const slotKey = `SLOT-${park.id}-${s.id}`;
                const isAvail =
                  u.availableIds.has(slotKey) ||
                  u.availableIds.has(String(s.id));
                return {
                  ...s,
                  status: isAvail ? 'available' : 'occupied',
                };
              }),
            };
          })
        );
      } catch (e) {
        console.log('Sync error (Spring Boot @8080):', e.message);
      }
    }, 5000);

    return () => clearInterval(syncInterval);
  }, [currentScreen]);

  const handleNavigate = (screen, data = null) => {
    if (screen === 'Slots') setSelectedParkingId(data.id);
    if (screen === 'Payment') setPaymentData(data);
    setCurrentScreen(screen);
  };

  const finalizeBooking = () => {
    const updated = parkingData.map((p) =>
      p.id === selectedParkingId
        ? {
            ...p,
            slots: p.slots.map((s) =>
              s.id === paymentData.selectedSlot
                ? { ...s, status: 'reserved' }
                : s
            ),
          }
        : p
    );
    setParkingData(updated);
    setReservations([
      { ...paymentData, date: new Date().toLocaleDateString() },
      ...reservations,
    ]);
    Alert.alert('Başarılı', 'Slot rezerve edildi!');
    setCurrentScreen('MainApp');
    setActiveTab('Reservations');
  };

  const renderContent = () => {
    if (activeTab === 'Profile') return <ProfileScreen user={registeredUser} />;
    if (activeTab === 'Settings')
      return <SettingsScreen onLogout={() => setCurrentScreen('Login')} />;
    if (activeTab === 'Reservations')
      return <ReservationsScreen reservations={reservations} />;
    if (activeTab === 'Admin') {
      return adminSelectedPark ? (
        <AdminDetailScreen
          otopark={adminSelectedPark}
          onBack={() => setAdminSelectedPark(null)}
        />
      ) : (
        <AdminScreen onEnterDetail={setAdminSelectedPark} />
      );
    }
    if (currentScreen === 'Slots')
      return (
        <SlotSelectionScreen
          selectedParking={parkingData.find((p) => p.id === selectedParkingId)}
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
            onLogin={(u, p) =>
              u.trim().toLowerCase() === 'admin' && p === '1234'
                ? setCurrentScreen('MainApp')
                : Alert.alert('Hata')
            }
            onNavigate={setCurrentScreen}
          />
        ) : currentScreen === 'Register' ? (
          <RegisterScreen
            onNavigate={setCurrentScreen}
            onRegister={() => setCurrentScreen('Login')}
          />
        ) : (
          <View style={{ flex: 1 }}>
            <View style={{ flex: 1 }}>{renderContent()}</View>
            <View style={styles.tabBar}>
              {[
                { id: 'Home', label: 'Otopark' },
                { id: 'Reservations', label: 'Rezervasyon' },
                { id: 'Profile', label: 'Profil' },
                { id: 'Admin', label: 'Panel' },
                { id: 'Settings', label: 'Ayarlar' },
              ].map((tab) => (
                <TouchableOpacity
                  key={tab.id}
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
