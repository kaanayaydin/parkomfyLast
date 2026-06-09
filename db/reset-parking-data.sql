-- Tüm otopark verilerini siler; kullanıcılar ve push token'lar korunur.
USE parkomfy;

SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM slot_reservations;
DELETE FROM parking_sessions;
DELETE FROM detection_results;
DELETE FROM parking_slots;
DELETE FROM parking_areas;
SET FOREIGN_KEY_CHECKS = 1;
