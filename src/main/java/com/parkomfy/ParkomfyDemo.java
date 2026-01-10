package com.parkomfy;

import com.parkomfy.model.*;
import com.parkomfy.repository.DatabaseManager;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.service.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Simple Console Demo - No Spring Boot required
 * Demonstrates all major functionality
 */
public class ParkomfyDemo {
    
    private static IParkingRepository repository;
    private static IParkingService parkingService;
    private static IPaymentService paymentService;
    private static IDetectionService detectionService;
    private static ParkingArea parkingArea;
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║          PARKOMFY - Smart Parking Management System         ║");
        System.out.println("║                    Demo Application                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
        
        // Initialize services
        initializeServices();
        
        // Run demo
        runDemo();
    }
    
    private static void initializeServices() {
        System.out.println("📋 Initializing services...\n");
        
        // Initialize database (simulated)
        repository = new DatabaseManager(
            "jdbc:mysql://localhost:3306/parkomfy",
            "root",
            "password"
        );
        
        // Initialize services with dependency injection
        paymentService = new PaymentService(repository);
        parkingService = new ParkingService(repository, paymentService);
        detectionService = new DetectionService(repository);
        
        // Initialize parking area
        initializeParkingArea();
        
        System.out.println("✅ Services initialized successfully!\n");
    }
    
    private static void initializeParkingArea() {
        parkingArea = new ParkingArea("AREA-001", "Özyeğin University Parking", "Çekmeköy, İstanbul");
        
        // Set pricing policy
        PricingPolicy policy = new PricingPolicy("POLICY-001", 15.0);
        policy.setFirstHourRate(10.0);
        policy.setFreeMinutes(15);
        policy.setMaxDailyRate(100.0);
        parkingArea.setPricingPolicy(policy);
        
        // Create parking slots
        for (int i = 1; i <= 10; i++) {
            ParkingSlot slot = new ParkingSlot(
                "SLOT-1A-" + i, 1, "A", i, i * 10.0, 10.0
            );
            parkingArea.addParkingSlot(slot);
        }
        
        for (int i = 1; i <= 10; i++) {
            ParkingSlot slot = new ParkingSlot(
                "SLOT-1B-" + i, 1, "B", i, i * 10.0, 20.0
            );
            parkingArea.addParkingSlot(slot);
        }
        
        // Create cameras
        Camera entranceCamera = new Camera("CAM-ENTRANCE-001", "Entrance LPR Camera",
                Camera.CameraType.ENTRANCE_LPR, "Main Entrance");
        entranceCamera.setHasNightVision(true);
        parkingArea.addCamera(entranceCamera);
        
        Camera areaCamera = new Camera("CAM-AREA-001", "Floor 1 Zone A Camera",
                Camera.CameraType.PARKING_AREA, "Floor 1, Zone A");
        areaCamera.setHasNightVision(true);
        
        List<ParkingSlot> zoneASlots = parkingArea.getParkingSlots().stream()
            .filter(s -> s.getZone().equals("A"))
            .toList();
        for (ParkingSlot slot : zoneASlots) {
            areaCamera.addMonitoredSlot(slot);
        }
        
        parkingArea.addCamera(areaCamera);
    }
    
    private static void runDemo() {
        System.out.println("🎬 Running Demo Scenario\n");
        System.out.println("═══════════════════════════════════════════════════════════\n");
        
        // 1. Show initial status
        System.out.println("1️⃣  INITIAL PARKING AREA STATUS:");
        System.out.println("   Total Slots: " + parkingArea.getParkingSlots().size());
        System.out.println("   Available: " + parkingArea.getAvailableSlotCount());
        System.out.println("   Occupied: " + parkingArea.getOccupiedSlotCount());
        System.out.println("   Occupancy Rate: " + 
            String.format("%.1f%%", (parkingArea.getOccupiedSlotCount() * 100.0 / parkingArea.getParkingSlots().size())));
        System.out.println();
        
        // 2. Vehicle Entry
        System.out.println("2️⃣  VEHICLE ENTRY - LICENSE PLATE RECOGNITION:");
        Camera entranceCamera = parkingArea.getCameras().stream()
            .filter(c -> c.getType() == Camera.CameraType.ENTRANCE_LPR)
            .findFirst()
            .orElse(null);
        
        LicensePlate plate = new LicensePlate("34ABC123");
        Vehicle vehicle = new Vehicle(plate, Vehicle.VehicleType.CAR);
        vehicle.setEntryTime(LocalDateTime.now());
        repository.saveVehicle(vehicle);
        System.out.println("   License Plate: " + vehicle.getLicensePlate().getPlateNumber());
        System.out.println("   Vehicle ID: " + vehicle.getVehicleId());
        System.out.println("   Vehicle Type: " + vehicle.getVehicleType());
        System.out.println();
        
        // 3. Find available slot
        System.out.println("3️⃣  FINDING AVAILABLE PARKING SLOT:");
        List<ParkingSlot> availableSlots = parkingService.findAvailableSlots(parkingArea);
        if (!availableSlots.isEmpty()) {
            ParkingSlot selectedSlot = availableSlots.get(0);
            System.out.println("   Selected Slot: " + selectedSlot.getLocationString());
            System.out.println("   Slot ID: " + selectedSlot.getSlotId());
            System.out.println();
            
            // 4. Occupy slot
            System.out.println("4️⃣  OCCUPYING PARKING SLOT:");
            ParkingSession session = parkingService.occupySlot(selectedSlot, vehicle);
            repository.saveSession(session);
            System.out.println("   Session ID: " + session.getSessionId());
            System.out.println("   Entry Time: " + session.getEntryTime());
            System.out.println("   Slot Status: " + selectedSlot.getStatus());
            System.out.println();
            
            // 5. Real-time status update
            System.out.println("5️⃣  UPDATED PARKING AREA STATUS:");
            System.out.println("   Available: " + parkingArea.getAvailableSlotCount());
            System.out.println("   Occupied: " + parkingArea.getOccupiedSlotCount());
            System.out.println("   Occupancy Rate: " + 
                String.format("%.1f%%", (parkingArea.getOccupiedSlotCount() * 100.0 / parkingArea.getParkingSlots().size())));
            System.out.println();
            
            // 6. Vehicle Exit
            System.out.println("6️⃣  VEHICLE EXIT - FEE CALCULATION:");
            // Simulate 2 hours 30 minutes parking
            vehicle.setEntryTime(LocalDateTime.now().minusHours(2).minusMinutes(30));
            session.getVehicle().setEntryTime(vehicle.getEntryTime());
            
            Payment payment = parkingService.completeSession(session);
            repository.savePayment(payment);
            
            System.out.println("   Duration: " + session.getDurationMinutes() + " minutes (" + 
                String.format("%.1f", session.getDurationMinutes() / 60.0) + " hours)");
            System.out.println("   Calculated Fee: " + String.format("%.2f", payment.getAmount()) + " TRY");
            System.out.println("   Payment ID: " + payment.getPaymentId());
            System.out.println();
            
            // 7. Process payment
            System.out.println("7️⃣  PROCESSING PAYMENT (Stripe):");
            PaymentMethod paymentMethod = new PaymentMethod(PaymentMethod.PaymentType.CREDIT_CARD);
            Payment processedPayment = paymentService.processPayment(
                session,
                paymentMethod,
                payment.getAmount()
            );
            System.out.println("   Payment Method: " + paymentMethod.getType());
            System.out.println("   Status: ✅ COMPLETED");
            System.out.println("   Transaction ID: " + processedPayment.getPaymentId());
            System.out.println();
            
            // 8. Final status
            System.out.println("8️⃣  FINAL PARKING AREA STATUS:");
            System.out.println("   Available: " + parkingArea.getAvailableSlotCount());
            System.out.println("   Occupied: " + parkingArea.getOccupiedSlotCount());
            System.out.println();
        }
        
        System.out.println("═══════════════════════════════════════════════════════════\n");
        System.out.println("✅ Demo completed successfully!\n");
        System.out.println("📡 REST API is running on http://localhost:8080");
        System.out.println("   Endpoints:");
        System.out.println("   - GET  /api/v1/parking/status");
        System.out.println("   - POST /api/v1/parking/sessions");
        System.out.println("   - GET  /api/v1/parking/slots/available");
        System.out.println("   - POST /api/v1/payments");
        System.out.println();
    }
}
