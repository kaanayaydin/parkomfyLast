package com.parkomfy.config;

import com.parkomfy.service.NotificationService;
import com.parkomfy.service.OccupancySyncService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private final NotificationService notificationService;
    private final OccupancySyncService occupancySyncService;

    public SchedulingConfig(NotificationService notificationService,
                            OccupancySyncService occupancySyncService) {
        this.notificationService = notificationService;
        this.occupancySyncService = occupancySyncService;
    }

    @Scheduled(fixedRate = 60000)
    public void reservationReminders() {
        notificationService.processReservationReminders();
    }

    /** Test: 5 sn doluluk senkronu (ileride aralık uzatılacak). */
    @Scheduled(fixedRate = 5000)
    public void occupancySync() {
        occupancySyncService.syncAllCalibratedAreas();
    }
}
