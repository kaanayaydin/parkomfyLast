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
        // Use gRPC mode: connect to Python service on localhost:50051
        // For testing without Python service, use: new YOLOInference()
        return new YOLOInference("localhost", 50051);
    }
    
    @Bean
    public IDetectionService detectionService(IParkingRepository repository,
                                             YOLOInference yoloInference) {
        return new DetectionService(repository, yoloInference);
    }
    
    @Bean
    public ParkingApiController parkingApiController(
            IParkingService parkingService,
            IPaymentService paymentService,
            IDetectionService detectionService,
            IParkingRepository repository) {
        return new ParkingApiController(parkingService, paymentService, 
                                       detectionService, repository);
    }
}
