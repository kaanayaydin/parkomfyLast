package com.parkomfy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.parkomfy.api.*;
import com.parkomfy.repository.*;
import com.parkomfy.service.*;
import com.parkomfy.ai.YOLOInference;

/**
 * Spring Boot Main Application
 * Initializes all services and REST API
 */
@SpringBootApplication
public class ParkomfySpringApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ParkomfySpringApplication.class, args);
    }
    
    // ============================================
    // BEAN DEFINITIONS
    // ============================================
    
    @Bean
    public IParkingRepository parkingRepository() {
        return new DatabaseManager(
            "jdbc:mysql://localhost:3306/parkomfy",
            "root",
            "password"
        );
    }
    
    @Bean
    public IPaymentService paymentService(IParkingRepository repository) {
        return new PaymentService(repository);
    }
    
    @Bean
    public IParkingService parkingService(IParkingRepository repository, 
                                         IPaymentService paymentService) {
        return new ParkingService(repository, paymentService);
    }
    
    @Bean
    public YOLOInference yoloInference() {
        // 127.0.0.1 = Python gRPC sunucusu (grpc_server/server.py)
        return new YOLOInference("127.0.0.1", 50051);
    }
    
    @Bean
    public IDetectionService detectionService(IParkingRepository repository,
                                             YOLOInference yoloInference) {
        return new DetectionService(repository, yoloInference);
    }

    @Bean
    public LiveParkingService liveParkingService(IParkingRepository repository) {
        return new LiveParkingService(repository);
    }

    @Bean
    public ReservationAvailabilityService reservationAvailabilityService(
            IParkingRepository repository,
            OccupancySyncService occupancySyncService) {
        return new ReservationAvailabilityService(repository, occupancySyncService);
    }

    @Bean
    public ReservationService reservationService(IParkingRepository repository,
                                                 LiveParkingService liveParkingService,
                                                 ReservationAvailabilityService availabilityService) {
        ReservationService service = new ReservationService(repository);
        service.setLiveParkingService(liveParkingService);
        service.setAvailabilityService(availabilityService);
        return service;
    }

    @Bean
    public ParkingEventBroadcaster parkingEventBroadcaster() {
        return new ParkingEventBroadcaster();
    }

    @Bean
    public NotificationService notificationService(IParkingRepository repository,
                                                   ParkingEventBroadcaster broadcaster) {
        return new NotificationService(repository, broadcaster);
    }

    @Bean
    public PlateTrackingService plateTrackingService(IParkingRepository repository,
                                                     IDetectionService detectionService,
                                                     YOLOInference yoloInference,
                                                     LiveParkingService liveParkingService,
                                                     ParkingEventBroadcaster broadcaster,
                                                     NotificationService notificationService) {
        return new PlateTrackingService(repository, detectionService, yoloInference,
            liveParkingService, broadcaster, notificationService);
    }

    @Bean
    public CameraSimulationService cameraSimulationService() {
        return new CameraSimulationService();
    }

    @Bean
    public ParkingSetupService parkingSetupService(IParkingRepository repository,
                                                   YOLOInference yoloInference) {
        return new ParkingSetupService(repository, yoloInference);
    }

    @Bean
    public PlateSimulationService plateSimulationService(IParkingRepository repository) {
        return new PlateSimulationService(repository);
    }

    @Bean
    public OccupancySyncService occupancySyncService(IParkingRepository repository,
                                                       YOLOInference yoloInference,
                                                       CameraSimulationService cameraSimulationService,
                                                       LiveParkingService liveParkingService,
                                                       ParkingEventBroadcaster broadcaster,
                                                       PlateSimulationService plateSimulationService) {
        return new OccupancySyncService(repository, yoloInference, cameraSimulationService,
            liveParkingService, broadcaster, plateSimulationService);
    }

    @Bean
    public AuthService authService(IParkingRepository repository) {
        return new AuthService(repository);
    }
    
    @Bean
    public ParkingApiController parkingApiController(
            IParkingService parkingService,
            IPaymentService paymentService,
            IDetectionService detectionService,
            IParkingRepository repository,
            ReservationService reservationService,
            LiveParkingService liveParkingService,
            PlateTrackingService plateTrackingService,
            NotificationService notificationService,
            ParkingEventBroadcaster broadcaster,
            ParkingSetupService parkingSetupService,
            PlateSimulationService plateSimulationService,
            OccupancySyncService occupancySyncService,
            ReservationAvailabilityService reservationAvailabilityService) {
        return new ParkingApiController(parkingService, paymentService, 
                                       detectionService, repository, reservationService,
                                       liveParkingService, plateTrackingService,
                                       notificationService, broadcaster, parkingSetupService,
                                       plateSimulationService, occupancySyncService,
                                       reservationAvailabilityService);
    }
}
