package com.parkomfy.util;

import java.time.LocalDateTime;

public final class TimeRangeUtil {

    private TimeRangeUtil() {}

    /** Sıfır tolerans: 1 dakika bile örtüşme varsa true. Bitiş=başlangıç temas etmiyor sayılır. */
    public static boolean overlaps(LocalDateTime startA, LocalDateTime endA,
                                   LocalDateTime startB, LocalDateTime endB) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        if (!endA.isAfter(startA) || !endB.isAfter(startB)) {
            return false;
        }
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    /** Seçilen aralık şu anı kapsıyor mu (canlı doluluk kontrolü). */
    public static boolean rangeIncludesNow(LocalDateTime rangeStart, LocalDateTime rangeEnd, LocalDateTime now) {
        return !rangeStart.isAfter(now) && rangeEnd.isAfter(now);
    }
}
