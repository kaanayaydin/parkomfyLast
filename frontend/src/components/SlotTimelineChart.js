import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';

const STATUS_COLORS = {
  FREE: '#4CAF50',
  RESERVED: '#FB8C00',
  LIVE_OCCUPIED: '#E53935',
  LIVE_RESERVED: '#D84315',
  SELECTED: '#1A237E',
};

function parseTs(iso) {
  if (!iso) return 0;
  const s = iso.length === 19 ? iso : iso.substring(0, 19);
  return new Date(s).getTime();
}

function formatHour(iso) {
  if (!iso) return '';
  const s = iso.length === 19 ? iso : iso.substring(0, 19);
  const d = new Date(s);
  return d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
}

/**
 * Saatlik doluluk çizelgesi — rezervasyon (turuncu) ve canlı dolu (kırmızı) ayrı.
 */
const SlotTimelineChart = ({ timeline = [], reservations = [], rangeStart, rangeEnd, title }) => {
  const rs = parseTs(rangeStart);
  const re = parseTs(rangeEnd);

  if (!timeline.length) {
    return <Text style={styles.empty}>Zaman çizelgesi yok</Text>;
  }

  return (
    <View style={styles.wrap}>
      {title ? <Text style={styles.title}>{title}</Text> : null}
      <View style={styles.legend}>
        <Text style={styles.legendItem}><Text style={[styles.dot, { color: STATUS_COLORS.FREE }]}>●</Text> Boş</Text>
        <Text style={styles.legendItem}><Text style={[styles.dot, { color: STATUS_COLORS.RESERVED }]}>●</Text> Rezerve</Text>
        <Text style={styles.legendItem}><Text style={[styles.dot, { color: STATUS_COLORS.LIVE_OCCUPIED }]}>●</Text> Canlı dolu</Text>
        <Text style={styles.legendItem}><Text style={[styles.dot, { color: STATUS_COLORS.LIVE_RESERVED }]}>●</Text> Rezerve+canlı</Text>
      </View>
      {reservations.length > 0 && (
        <View style={styles.resList}>
          {reservations.map((r) => (
            <Text key={r.reservationId || `${r.startTime}-${r.licensePlate}`} style={styles.resLine}>
              Rezerve: {formatHour(r.startTime)} – {formatHour(r.endTime)} · {r.licensePlate || '?'}
            </Text>
          ))}
        </View>
      )}
      <ScrollView horizontal showsHorizontalScrollIndicator={false}>
        <View style={styles.row}>
          {timeline.map((seg, i) => {
            const segStart = parseTs(seg.startTime);
            const segEnd = parseTs(seg.endTime);
            const inRange = segEnd > rs && segStart < re;
            let color = STATUS_COLORS[seg.status] || STATUS_COLORS.FREE;
            if (inRange && seg.status === 'FREE') {
              color = '#B2FF59';
            }
            return (
              <View key={`${seg.startTime}-${i}`} style={styles.segWrap}>
                <View style={[styles.seg, { backgroundColor: color }, inRange && styles.segSelected]}>
                  <Text style={styles.segLabel} numberOfLines={1}>{seg.label || seg.status}</Text>
                </View>
                <Text style={styles.hour}>{formatHour(seg.startTime)}</Text>
              </View>
            );
          })}
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  wrap: { marginTop: 8, marginBottom: 12 },
  title: { fontSize: 13, fontWeight: '700', color: '#1A237E', marginBottom: 6 },
  legend: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 8 },
  legendItem: { fontSize: 11, color: '#666' },
  dot: { fontSize: 12 },
  row: { flexDirection: 'row', alignItems: 'flex-end', paddingVertical: 4 },
  segWrap: { alignItems: 'center', marginRight: 4, width: 52 },
  seg: {
    width: 48,
    height: 36,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.08)',
  },
  segSelected: { borderWidth: 2, borderColor: '#1A237E' },
  segLabel: { fontSize: 8, color: '#FFF', fontWeight: '700', textAlign: 'center' },
  hour: { fontSize: 9, color: '#888', marginTop: 4 },
  empty: { fontSize: 12, color: '#999', fontStyle: 'italic' },
  resList: { marginBottom: 8, backgroundColor: '#FFF8E1', padding: 8, borderRadius: 8 },
  resLine: { fontSize: 11, color: '#E65100', marginBottom: 2 },
});

export default SlotTimelineChart;
