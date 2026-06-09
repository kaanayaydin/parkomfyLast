import React, { useState, useEffect, useMemo } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  SafeAreaView, ActivityIndicator, Platform, Modal, Pressable,
} from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import {
  getLiveParkingStatus,
  buildDateTimeFromDate,
  formatDateLabel,
  formatTimeLabel,
  startOfToday,
  applyDatePart,
  applyTimePart,
} from '../config/api';

const PICKER_META = {
  startDate: { title: 'Başlangıç Tarihi', mode: 'date' },
  startTime: { title: 'Başlangıç Saati', mode: 'time' },
  endDate: { title: 'Bitiş Tarihi', mode: 'date' },
  endTime: { title: 'Bitiş Saati', mode: 'time' },
};

function SelectButton({ label, value, onPress }) {
  return (
    <View style={styles.pickerField}>
      <Text style={styles.subLabel}>{label}</Text>
      <TouchableOpacity style={styles.pickerBtn} onPress={onPress} activeOpacity={0.7}>
        <Text style={styles.pickerBtnText} numberOfLines={1}>{value}</Text>
        <Text style={styles.pickerBtnHint}>›</Text>
      </TouchableOpacity>
    </View>
  );
}

function makeDefaultStart() {
  const d = new Date();
  d.setSeconds(0, 0);
  d.setMinutes(0);
  d.setHours(d.getHours() + 1);
  return d;
}

function startOfDayFrom(ref) {
  const d = new Date(ref);
  d.setHours(0, 0, 0, 0);
  return d;
}

const SlotSelectionScreen = ({ onNavigate, selectedParking, areaId, licensePlate }) => {
  const today = useMemo(() => startOfToday(), []);
  const [startAt, setStartAt] = useState(makeDefaultStart);
  const [endAt, setEndAt] = useState(() => {
    const d = makeDefaultStart();
    d.setHours(d.getHours() + 2);
    return d;
  });
  const [activePicker, setActivePicker] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [availableIds, setAvailableIds] = useState(new Set());
  const [loading, setLoading] = useState(false);

  const startTime = useMemo(() => buildDateTimeFromDate(startAt), [startAt]);
  const endTime = useMemo(() => buildDateTimeFromDate(endAt), [endAt]);
  const isValidRange = endAt.getTime() > startAt.getTime();

  const unitPrice = selectedParking?.price || 20;
  const durationHours = useMemo(() => {
    const diff = (endAt.getTime() - startAt.getTime()) / (1000 * 60 * 60);
    return Math.max(1, Math.ceil(diff));
  }, [startAt, endAt]);
  const totalFee = durationHours * unitPrice;

  const pickerConfig = useMemo(() => {
    if (!activePicker) return null;
    const meta = PICKER_META[activePicker];
    const isStart = activePicker.startsWith('start');
    const isDate = meta.mode === 'date';
    return {
      ...meta,
      value: isStart ? startAt : endAt,
      minimumDate: isDate
        ? (isStart ? today : startOfDayFrom(startAt))
        : undefined,
    };
  }, [activePicker, startAt, endAt, today]);

  const handlePickerChange = (event, selected) => {
    if (Platform.OS === 'android') {
      setActivePicker(null);
      if (event?.type === 'dismissed' || !selected) return;
    }
    if (!selected) return;

    if (activePicker === 'startDate') setStartAt(applyDatePart(startAt, selected));
    if (activePicker === 'startTime') setStartAt(applyTimePart(startAt, selected));
    if (activePicker === 'endDate') setEndAt(applyDatePart(endAt, selected));
    if (activePicker === 'endTime') setEndAt(applyTimePart(endAt, selected));
  };

  useEffect(() => {
    if (!areaId || !isValidRange) {
      setAvailableIds(new Set());
      return;
    }
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const res = await getLiveParkingStatus(areaId, startTime, endTime);
        if (!cancelled && res.success) {
          const ids = (res.data?.slots || [])
            .filter((s) => s.availableForBooking)
            .map((s) => s.slotId);
          setAvailableIds(new Set(ids));
        }
      } catch {
        if (!cancelled) setAvailableIds(new Set());
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [areaId, startTime, endTime, isValidRange]);

  const resolveSlotId = (slot) => slot.slotId || null;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
      >
        <Text style={styles.title}>{selectedParking?.name}</Text>

        <Text style={styles.sectionTitle}>Başlangıç</Text>
        <View style={styles.row2}>
          <SelectButton
            label="Tarih"
            value={formatDateLabel(startAt)}
            onPress={() => setActivePicker('startDate')}
          />
          <SelectButton
            label="Saat"
            value={formatTimeLabel(startAt)}
            onPress={() => setActivePicker('startTime')}
          />
        </View>

        <Text style={styles.sectionTitle}>Bitiş</Text>
        <View style={styles.row2}>
          <SelectButton
            label="Tarih"
            value={formatDateLabel(endAt)}
            onPress={() => setActivePicker('endDate')}
          />
          <SelectButton
            label="Saat"
            value={formatTimeLabel(endAt)}
            onPress={() => setActivePicker('endTime')}
          />
        </View>

        {!isValidRange && (
          <Text style={styles.error}>
            Bitiş tarih ve saati, başlangıçtan sonra olmalıdır.
          </Text>
        )}

        <Text style={styles.label}>Slot Seçin (Kırmızı: seçilen aralıkta dolu)</Text>
        {loading ? (
          <ActivityIndicator color="#1A237E" style={{ marginVertical: 20 }} />
        ) : (
          <View style={styles.grid}>
            {selectedParking?.slots?.map((slot) => {
              const slotId = resolveSlotId(slot);
              const available = slotId ? availableIds.has(slotId) : false;
              return (
                <TouchableOpacity
                  key={slotId || slot.id}
                  disabled={!available || !isValidRange}
                  style={[
                    styles.slot,
                    selectedSlot === slot.id && styles.activeSlot,
                    !available && styles.occSlot,
                  ]}
                  onPress={() => setSelectedSlot(slot.id)}
                >
                  <Text style={[styles.slotText, !available && styles.occText]}>{slot.id}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        )}
      </ScrollView>

      <View style={styles.footer}>
        <View style={styles.footerInfo}>
          <Text style={styles.rangeHint} numberOfLines={2}>
            {durationHours} saat · {formatDateLabel(startAt)} {formatTimeLabel(startAt)}
            {' → '}
            {formatDateLabel(endAt)} {formatTimeLabel(endAt)}
          </Text>
          <Text style={styles.total}>{totalFee} TL</Text>
        </View>
        <TouchableOpacity
          style={[styles.confirm, (!selectedSlot || !isValidRange) && styles.confirmDisabled]}
          onPress={() => onNavigate('Payment', {
            totalFee,
            selectedSlot,
            slotId: selectedParking?.slots?.find((s) => s.id === selectedSlot)?.slotId,
            areaId,
            licensePlate,
            startTime,
            endTime,
            durationHours,
            parkingName: selectedParking?.name,
          })}
          disabled={!selectedSlot || !isValidRange}
        >
          <Text style={styles.confirmTxt}>Ödemeye Geç</Text>
        </TouchableOpacity>
      </View>

      {/* Android: native dialog */}
      {activePicker && Platform.OS === 'android' && pickerConfig && (
        <DateTimePicker
          value={pickerConfig.value}
          mode={pickerConfig.mode}
          minimumDate={pickerConfig.minimumDate}
          is24Hour
          onChange={handlePickerChange}
        />
      )}

      {/* iOS: bottom sheet modal — tek picker, taşma yok */}
      {Platform.OS === 'ios' && (
        <Modal
          visible={!!activePicker}
          transparent
          animationType="slide"
          onRequestClose={() => setActivePicker(null)}
        >
          <Pressable style={styles.modalOverlay} onPress={() => setActivePicker(null)}>
            <Pressable
              style={[
                styles.modalSheet,
                pickerConfig?.mode === 'date' ? styles.modalSheetDate : styles.modalSheetTime,
              ]}
              onPress={(e) => e.stopPropagation()}
            >
              <View style={styles.modalHeader}>
                <Text style={styles.modalTitle}>{pickerConfig?.title}</Text>
                <TouchableOpacity onPress={() => setActivePicker(null)}>
                  <Text style={styles.modalDone}>Tamam</Text>
                </TouchableOpacity>
              </View>
              {pickerConfig && (
                <DateTimePicker
                  value={pickerConfig.value}
                  mode={pickerConfig.mode}
                  display="spinner"
                  minimumDate={pickerConfig.minimumDate}
                  locale="tr-TR"
                  style={styles.iosPicker}
                  onChange={handlePickerChange}
                />
              )}
            </Pressable>
          </Pressable>
        </Modal>
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  scrollContent: { padding: 20, paddingBottom: 8 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A237E', marginBottom: 12 },
  sectionTitle: { fontSize: 15, fontWeight: 'bold', color: '#1A237E', marginTop: 8, marginBottom: 8 },
  label: { fontSize: 13, fontWeight: 'bold', color: '#666', marginBottom: 8, marginTop: 16 },
  subLabel: { fontSize: 12, fontWeight: '600', color: '#666', marginBottom: 6 },
  pickerField: { flex: 1, minWidth: 0 },
  row2: { flexDirection: 'row', gap: 10, marginBottom: 4 },
  pickerBtn: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#FFF',
    borderWidth: 1,
    borderColor: '#1A237E',
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 12,
    minHeight: 48,
  },
  pickerBtnText: { fontSize: 13, fontWeight: '600', color: '#1A237E', flex: 1 },
  pickerBtnHint: { fontSize: 18, color: '#1A237E', fontWeight: 'bold', marginLeft: 4 },
  error: { color: '#D32F2F', marginTop: 8, fontSize: 13, fontWeight: '600' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', paddingBottom: 8 },
  slot: { backgroundColor: '#FFF', padding: 20, borderRadius: 15, width: '46%', marginBottom: 15, alignItems: 'center', borderWidth: 1, borderColor: '#EEE' },
  activeSlot: { borderColor: '#B2FF59', borderWidth: 2 },
  occSlot: { backgroundColor: '#FFEBEE', borderColor: '#FFCDD2' },
  slotText: { fontWeight: 'bold', color: '#1A237E' },
  occText: { color: '#EF5350' },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderTopWidth: 1,
    borderColor: '#EEE',
    paddingHorizontal: 20,
    paddingVertical: 14,
    backgroundColor: '#F5F5F5',
  },
  footerInfo: { flex: 1, marginRight: 12, minWidth: 0 },
  rangeHint: { fontSize: 11, color: '#888', marginBottom: 4 },
  total: { fontSize: 22, fontWeight: 'bold', color: '#1A237E' },
  confirm: { backgroundColor: '#1A237E', paddingVertical: 16, paddingHorizontal: 18, borderRadius: 15 },
  confirmDisabled: { opacity: 0.5 },
  confirmTxt: { color: '#B2FF59', fontWeight: 'bold' },
  modalOverlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  modalSheet: {
    backgroundColor: '#FFF',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    paddingBottom: 28,
  },
  modalSheetDate: { maxHeight: 340 },
  modalSheetTime: { maxHeight: 300 },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#EEE',
  },
  modalTitle: { fontSize: 16, fontWeight: 'bold', color: '#1A237E' },
  modalDone: { fontSize: 16, fontWeight: 'bold', color: '#1A237E' },
  iosPicker: { width: '100%', height: 216 },
});

export default SlotSelectionScreen;
