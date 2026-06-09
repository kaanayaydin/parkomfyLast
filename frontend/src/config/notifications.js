import { Platform } from 'react-native';
import Constants, { ExecutionEnvironment } from 'expo-constants';
import { registerPushToken } from './api';

// Expo Go (SDK 53+) removed Android remote push support; statically importing
// expo-notifications there throws at load time. We lazily require it and skip
// entirely on Expo Go + Android so the app runs without the console error.
const isExpoGo = Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
const pushSupported = !(isExpoGo && Platform.OS === 'android');

let Notifications = null;
let handlerSet = false;

function getNotifications() {
  if (!pushSupported) return null;
  if (!Notifications) {
    Notifications = require('expo-notifications');
    if (!handlerSet) {
      Notifications.setNotificationHandler({
        handleNotification: async () => ({
          shouldShowAlert: true,
          shouldPlaySound: true,
          shouldSetBadge: false,
        }),
      });
      handlerSet = true;
    }
  }
  return Notifications;
}

export async function setupPushNotifications(userId) {
  if (!userId) return null;
  const N = getNotifications();
  if (!N) return null;
  try {
    const { status: existing } = await N.getPermissionsAsync();
    let finalStatus = existing;
    if (existing !== 'granted') {
      const { status } = await N.requestPermissionsAsync();
      finalStatus = status;
    }
    if (finalStatus !== 'granted') return null;

    if (Platform.OS === 'android') {
      await N.setNotificationChannelAsync('parkomfy', {
        name: 'PARKOMFY',
        importance: N.AndroidImportance.HIGH,
      });
    }

    const tokenData = await N.getExpoPushTokenAsync();
    const token = tokenData.data;
    await registerPushToken(userId, token);
    return token;
  } catch (e) {
    console.log('Push setup failed:', e.message);
    return null;
  }
}

export function addNotificationListener(handler) {
  const N = getNotifications();
  if (!N) return () => {};
  const sub = N.addNotificationReceivedListener(handler);
  return () => sub.remove();
}
