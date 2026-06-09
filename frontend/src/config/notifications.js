import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { registerPushToken } from './api';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export async function setupPushNotifications(userId) {
  if (!userId) return null;
  try {
    const { status: existing } = await Notifications.getPermissionsAsync();
    let finalStatus = existing;
    if (existing !== 'granted') {
      const { status } = await Notifications.requestPermissionsAsync();
      finalStatus = status;
    }
    if (finalStatus !== 'granted') return null;

    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('parkomfy', {
        name: 'PARKOMFY',
        importance: Notifications.AndroidImportance.HIGH,
      });
    }

    const tokenData = await Notifications.getExpoPushTokenAsync();
    const token = tokenData.data;
    await registerPushToken(userId, token);
    return token;
  } catch (e) {
    console.log('Push setup failed:', e.message);
    return null;
  }
}

export function addNotificationListener(handler) {
  const sub = Notifications.addNotificationReceivedListener(handler);
  return () => sub.remove();
}
