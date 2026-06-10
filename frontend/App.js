import React, { useState, useEffect, useRef } from 'react';
import { View, Alert, StyleSheet, Text, TouchableOpacity } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import LoginScreen from './src/screens/LoginScreen';
import RegisterScreen from './src/screens/RegisterScreen';
import HomeScreen from './src/screens/HomeScreen';
import SlotSelectionScreen from './src/screens/SlotSelectionScreen';
import PaymentSummaryScreen from './src/screens/PaymentSummaryScreen';
import ProfileScreen from './src/screens/ProfileScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import ReservationsScreen from './src/screens/ReservationsScreen';
import PaymentsScreen from './src/screens/PaymentsScreen';
import EntranceScreen from './src/screens/EntranceScreen';
import AdminScreen from './src/screens/AdminScreen';
import AdminDetailScreen from './src/screens/AdminDetailScreen';
import AdminReservationsScreen from './src/screens/AdminReservationsScreen';
import {
  getLiveParkingStatus,
  getPublicParkingAreas,
  createReservation,
  getReservations,
  cancelReservation,
  buildDateTime,
  registerUser,
  loginUser,
  mapUserFromApi,
  connectParkingWebSocket,
  payWalkInPayment,
} from './src/config/api';
import { setupPushNotifications } from './src/config/notifications';

const DRIVER_TABS = [
  { id: 'Home', label: 'Otopark' },
  { id: 'Reservations', label: 'Rezervasyon' },
  { id: 'Payments', label: 'Ödemelerim' },
  { id: 'Profile', label: 'Profil' },
  { id: 'Settings', label: 'Ayarlar' },
];

const ADMIN_TABS = [
  { id: 'AdminHome', label: 'Otoparklar' },
  { id: 'AdminReservations', label: 'Rezervasyonlar' },
  { id: 'Settings', label: 'Ayarlar' },
];

function isAdminUser(user) {
  return user?.email === 'admin' || user?.role === 'ADMIN';
}

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
  const [adminOtopark, setAdminOtopark] = useState(null);

  const [currentUser, setCurrentUser] = useState(null);

  const adminMode = isAdminUser(currentUser);
  const visibleTabs = adminMode ? ADMIN_TABS : DRIVER_TABS;

  const mapReservation = (r) => ({
    ...r,
    parkingName: r.areaId,
    selectedSlot: r.slotId?.split('-').pop(),
    durationHours: Math.ceil(
      (new Date(r.endTime) - new Date(r.startTime)) / (1000 * 60 * 60)
    ),
  });

  const loadReservations = async (plate) => {
    if (!plate) {
      setReservations([]);
      return;
    }
    try {
      const res = await getReservations(plate);
      if (res.success && res.data?.length) {
        setReservations(res.data.map(mapReservation));
      } else {
        setReservations([]);
      }
    } catch {
      setReservations([]);
    }
  };

  const mapAreasToLots = (areas) => (areas || []).map((a) => {
    const hourlyRate = a.hourlyRate ?? 20;
    const firstHourRate = a.firstHourRate ?? hourlyRate;
    return {
      id: a.areaId,
      areaId: a.areaId,
      name: a.areaName,
      location: a.address || '',
      lotKey: a.lotKey || 'loop1',
      price: hourlyRate,
      hourlyRate,
      firstHourRate,
      freeMinutes: a.freeMinutes ?? 0,
      maxDailyRate: a.maxDailyRate ?? null,
      slots: Array.from({ length: a.slotCount || 0 }, (_, i) => ({
        id: i + 1,
        status: 'available',
        slotId: `SLOT-${a.lotKey || 'loop1'}-${i + 1}`,
      })),
    };
  });

  const loadPublicAreas = async () => {
    try {
      const res = await getPublicParkingAreas();
      if (res.success && res.data?.length) {
        setParkingData(mapAreasToLots(res.data));
      } else {
        setParkingData([]);
      }
    } catch {
      setParkingData([]);
    }
  };

  const handleLogin = async (email, password) => {
    try {
      const res = await loginUser(email, password);
      if (res.success) {
        const user = mapUserFromApi(res.data);
        setCurrentUser(user);
        setActiveTab(isAdminUser(user) ? 'AdminHome' : 'Home');
        setCurrentScreen('MainApp');
        if (!isAdminUser(user)) {
          loadPublicAreas();
          setupPushNotifications(user.userId);
        }
      } else {
        Alert.alert('Giriş başarısız', res.message || 'E-posta veya şifre hatalı');
      }
    } catch {
      Alert.alert('Bağlantı hatası', 'Sunucuya ulaşılamadı. Spring Boot çalışıyor mu?');
    }
  };

  const handleRegister = async ({ email, password, plate, fullName, kvkkAccepted }) => {
    if (!fullName?.trim() || !email?.trim() || !plate?.trim() || !password) {
      Alert.alert('Eksik bilgi', 'Tüm alanları doldurun');
      return;
    }
    if (!kvkkAccepted) {
      Alert.alert('KVKK onayı gerekli', 'Kayıt için aydınlatma metnini okuyup onaylamanız gerekir.');
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
    if (adminMode) return;
    loadPublicAreas();
  }, [currentScreen, adminMode]);

  useEffect(() => {
    if (currentScreen === 'Login' || currentScreen === 'Register') return;
    if (adminMode) return;
    loadReservations(currentUser?.plate);
  }, [currentScreen, currentUser?.plate, adminMode]);

  const parkingDataRef = useRef(parkingData);
  parkingDataRef.current = parkingData;

  useEffect(() => {
    if (currentScreen === 'Login' || currentScreen === 'Register') return;
    if (adminMode) return;

    const mapLiveSlot = (liveSlot) => {
      let status = 'available';
      if (liveSlot.mergedStatus === 'OCCUPIED') status = 'occupied';
      else if (liveSlot.mergedStatus === 'RESERVED') status = 'reserved';
      const showPlate = liveSlot.mergedStatus === 'OCCUPIED' || liveSlot.mergedStatus === 'RESERVED';
      return {
        id: liveSlot.slotNumber,
        slotId: liveSlot.slotId,
        status,
        displayLabel: liveSlot.displayLabel,
        licensePlate: showPlate ? liveSlot.licensePlate : null,
        availableForBooking: !!liveSlot.availableForBooking,
      };
    };

    const applyLiveData = (lotId, live) => {
      if (!live) return;
      setParkingData((prev) =>
        prev.map((park) => {
          if (park.id !== lotId) return park;
          const liveSlots = (live.slots || []).map(mapLiveSlot);
          return {
            ...park,
            occupiedCount: live.occupiedSlots ?? 0,
            availableCount: live.availableSlots ?? 0,
            slots: liveSlots.length ? liveSlots : park.slots,
          };
        })
      );
    };

    const syncLive = async () => {
      try {
        const lots = parkingDataRef.current;
        const hour = new Date().getHours();
        const startTime = buildDateTime(0, hour);
        const endTime = buildDateTime(0, Math.min(22, hour + 2));
        if (!lots.length) return;
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
        const lot = parkingDataRef.current.find(
          (p) => (p.areaId || p.id) === payload.data.areaId
        );
        if (lot) applyLiveData(lot.id, payload.data);
      }
    });

    return () => {
      clearInterval(syncInterval);
      disconnectWs();
    };
  }, [currentScreen, adminMode]);

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
      await loadReservations(currentUser?.plate);
      Alert.alert('Başarılı', 'Slot seçilen saat aralığı için rezerve edildi!');
      setCurrentScreen('MainApp');
      setActiveTab('Reservations');
    } catch (e) {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı. Spring Boot çalışıyor mu?');
    }
  };

  const finalizeWalkInPayment = async () => {
    try {
      const res = await payWalkInPayment(paymentData.paymentId);
      if (!res.success) {
        Alert.alert('Hata', res.message || 'Ödeme tamamlanamadı');
        return;
      }
      Alert.alert('Başarılı', 'Park ücreti ödendi!');
      setPaymentData(null);
      setCurrentScreen('MainApp');
      setActiveTab('Payments');
    } catch {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı. Spring Boot çalışıyor mu?');
    }
  };

  const handlePayWalkIn = (item) => {
    handleNavigate('Payment', {
      walkIn: true,
      paymentId: item.paymentId,
      sessionId: item.sessionId,
      parkingName: item.areaName || item.areaId || 'Otopark',
      selectedSlot: item.slotId ? item.slotId.split('-').pop() : '-',
      startTime: item.entryTime,
      endTime: item.exitTime,
      durationSeconds: item.durationSeconds,
      totalFee: item.amount,
    });
  };

  const handleCancelReservation = async (reservationId) => {
    try {
      const res = await cancelReservation(reservationId, currentUser?.plate);
      if (!res.success) {
        Alert.alert('İptal başarısız', res.message || 'Rezervasyon iptal edilemedi');
        return;
      }
      await loadReservations(currentUser?.plate);
      Alert.alert('İptal edildi', 'Rezervasyonunuz iptal edildi.');
    } catch {
      Alert.alert('Hata', 'Sunucuya bağlanılamadı. Spring Boot çalışıyor mu?');
    }
  };

  const renderContent = () => {
    if (adminMode) {
      if (currentScreen === 'AdminDetail' && adminOtopark) {
        return (
          <AdminDetailScreen
            otopark={adminOtopark}
            onBack={() => setCurrentScreen('MainApp')}
          />
        );
      }
      if (activeTab === 'AdminHome') {
        return (
          <AdminScreen
            onEnterDetail={(otopark) => {
              setAdminOtopark(otopark);
              setCurrentScreen('AdminDetail');
            }}
            onNewParking={() => Alert.alert(
              'Kalibrasyon',
              'Yeni otopark kalibrasyonu bilgisayardan http://sunucu:8080/admin adresinden yapılır.'
            )}
          />
        );
      }
      if (activeTab === 'AdminReservations') {
        return <AdminReservationsScreen />;
      }
      if (activeTab === 'Settings') {
        return <SettingsScreen onLogout={() => { setCurrentUser(null); setCurrentScreen('Login'); }} />;
      }
      return (
        <AdminScreen
          onEnterDetail={(otopark) => {
            setAdminOtopark(otopark);
            setCurrentScreen('AdminDetail');
          }}
          onNewParking={() => Alert.alert('Kalibrasyon', 'Web admin panelini kullanın: :8080/admin')}
        />
      );
    }

    if (currentScreen === 'Entrance') {
      return <EntranceScreen onBack={() => setCurrentScreen('MainApp')} />;
    }
    if (currentScreen === 'Slots') {
      return (
        <SlotSelectionScreen
          selectedParking={parkingData.find((p) => p.id === selectedParkingId)}
          areaId={parkingData.find((p) => p.id === selectedParkingId)?.areaId || selectedParkingId}
          licensePlate={currentUser?.plate}
          onNavigate={handleNavigate}
        />
      );
    }
    if (currentScreen === 'Payment') {
      return (
        <PaymentSummaryScreen
          paymentData={paymentData}
          isWalkIn={paymentData?.walkIn}
          onNavigate={paymentData?.walkIn ? finalizeWalkInPayment : finalizeBooking}
          onBack={() => {
            setPaymentData(null);
            setCurrentScreen('MainApp');
          }}
        />
      );
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
      return (
        <ReservationsScreen
          reservations={reservations}
          onCancel={handleCancelReservation}
        />
      );
    if (activeTab === 'Payments')
      return (
        <PaymentsScreen
          licensePlate={currentUser?.plate}
          onPay={handlePayWalkIn}
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
            {!(adminMode && currentScreen === 'AdminDetail') && (
            <View style={styles.tabBar}>
              {visibleTabs.map((tab) => (
                <TouchableOpacity
                  key={tab.id}
                  style={styles.tabItem}
                  activeOpacity={0.7}
                  hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}
                  onPress={() => {
                    setActiveTab(tab.id);
                    setCurrentScreen('MainApp');
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
            )}
          </View>
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}
