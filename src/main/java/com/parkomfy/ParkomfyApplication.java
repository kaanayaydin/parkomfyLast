package com.parkomfy;

import com.parkomfy.model.*;
import com.parkomfy.repository.DatabaseManager;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.service.*;

import java.util.List;

/**
 * Main application class for PARKOMFY system
 * Demonstrates OOP structure and system integration
 */
public class ParkomfyApplication {
    
    private IParkingRepository repository;
    private IParkingService parkingService;
    private IPaymentService paymentService;
    private IDetectionService detectionService;
    private ParkingArea parkingArea;
    
    public ParkomfyApplication() {
        // Initialize database (simulated)
        repository = new DatabaseManager(
            "jdbc:mysql://localhost:3306/parkomfy",
            "root",
            "password"
        );
        
        // Initialize services
        paymentService = new PaymentService(repository);
        parkingService = new ParkingService(repository, paymentService);
        detectionService = new DetectionService(repository);
        
        // Initialize parking area
        initializeParkingArea();
    }
    
    private void initializeParkingArea() {
        parkingArea = new ParkingArea("AREA-001", "Özyeğin University Parking", 
                                     "Çekmeköy, İstanbul");
        
        // Set pricing policy
        PricingPolicy policy = new PricingPolicy("POLICY-001", 15.0); // 15 TRY per hour
        policy.setFirstHourRate(10.0); // First hour: 10 TRY
        policy.setFreeMinutes(15); // 15 minutes free
        policy.setMaxDailyRate(100.0); // Max 100 TRY per day
        parkingArea.setPricingPolicy(policy);
        
        // Create parking slots
        createParkingSlots();
        
        // Create cameras
        createCameras();
    }
    
    private void createParkingSlots() {
        // Floor 1, Zone A
        for (int i = 1; i <= 10; i++) {
            ParkingSlot slot = new ParkingSlot(
                "SLOT-1A-" + i,
                1,
                "A",
                i,
                i * 10.0,
                10.0
            );
            parkingArea.addParkingSlot(slot);
        }
        
        // Floor 1, Zone B
        for (int i = 1; i <= 10; i++) {
            ParkingSlot slot = new ParkingSlot(
                "SLOT-1B-" + i,
                1,
                "B",
                i,
                i * 10.0,
                20.0
            );
            parkingArea.addParkingSlot(slot);
        }
    }
    
    private void createCameras() {
        // Entrance LPR camera
        Camera entranceCamera = new Camera(
            "CAM-ENTRANCE-001",
            "Entrance LPR Camera",
            Camera.CameraType.ENTRANCE_LPR,
            "Main Entrance"
        );
        entranceCamera.setHasNightVision(true);
        parkingArea.addCamera(entranceCamera);
        
        // Parking area camera
        Camera areaCamera = new Camera(
            "CAM-AREA-001",
            "Floor 1 Zone A Camera",
            Camera.CameraType.PARKING_AREA,
            "Floor 1, Zone A"
        );
        areaCamera.setHasNightVision(true);
        
        // Link camera to monitored slots
        List<ParkingSlot> zoneASlots = parkingArea.getParkingSlots().stream()
            .filter(s -> s.getZone().equals("A"))
            .toList();
        for (ParkingSlot slot : zoneASlots) {
            areaCamera.addMonitoredSlot(slot);
        }
        
        parkingArea.addCamera(areaCamera);
    }
    
    /**
     * Demo: Complete parking flow
     */
    public void demonstrateParkingFlow() {
        System.out.println("=== PARKOMFY System Demonstration ===\n");
        
        // 1. Show initial parking area status
        System.out.println("1. Initial Parking Area Status:");
        System.out.println(parkingArea);
        System.out.println("Available slots: " + parkingArea.getAvailableSlotCount());
        System.out.println();
        
        // 2. Vehicle enters - License Plate Recognition
        System.out.println("2. Vehicle Entry - License Plate Recognition:");
        Camera entranceCamera = parkingArea.getCameras().stream()
            .filter(c -> c.getType() == Camera.CameraType.ENTRANCE_LPR)
            .findFirst()
            .orElse(null);
        
        if (entranceCamera != null) {
            DetectionResult lprResult = detectionService.detectLicensePlate(entranceCamera);
            System.out.println("License Plate Detected: " + lprResult.getLicensePlateText());
            System.out.println("Confidence: " + String.format("%.2f%%", lprResult.getConfidence() * 100));
        }
        
        // Create vehicle
        LicensePlate plate = new LicensePlate("34ABC123");
        Vehicle vehicle = new Vehicle(plate, Vehicle.VehicleType.CAR);
        vehicle.setEntryTime(java.time.LocalDateTime.now());
        System.out.println("Vehicle created: " + vehicle);
        System.out.println();
        
        // 3. Find available slot
        System.out.println("3. Finding Available Parking Slot:");
        List<ParkingSlot> availableSlots = parkingService.findAvailableSlots(parkingArea);
        if (!availableSlots.isEmpty()) {
            ParkingSlot selectedSlot = availableSlots.get(0);
            System.out.println("Selected slot: " + selectedSlot.getLocationString());
            
            // 4. Occupy slot
            System.out.println("\n4. Occupying Parking Slot:");
            ParkingSession session = parkingService.occupySlot(selectedSlot, vehicle);
            System.out.println("Session created: " + session.getSessionId());
            System.out.println("Slot status: " + selectedSlot.getStatus());
            System.out.println();
            
            // 5. Real-time detection
            System.out.println("5. Real-time Detection (YOLO):");
            Camera areaCamera = parkingArea.getCameras().stream()
                .filter(c -> c.getType() == Camera.CameraType.PARKING_AREA)
                .findFirst()
                .orElse(null);
            
            if (areaCamera != null) {
                DetectionResult detection = detectionService.detectOccupancy(areaCamera, selectedSlot);
                System.out.println("Detection result: " + detection);
                System.out.println("Detection accuracy: " + 
                    String.format("%.2f%%", detectionService.getDetectionAccuracy()));
            }
            System.out.println();
            
            // 6. Vehicle exits - Calculate fee
            System.out.println("6. Vehicle Exit - Fee Calculation:");
            // Simulate 2 hours 30 minutes of parking (for demo purposes)
            vehicle.setEntryTime(java.time.LocalDateTime.now().minusHours(2).minusMinutes(30));
            session.getVehicle().setEntryTime(vehicle.getEntryTime());
            
            Payment payment = parkingService.completeSession(session);
            System.out.println("Parking duration: " + session.getDurationMinutes() + " minutes (" + 
                             String.format("%.1f", session.getDurationMinutes() / 60.0) + " hours)");
            System.out.println("Fee calculated: " + String.format("%.2f", payment.getAmount()) + " " + 
                parkingArea.getPricingPolicy().getCurrency());
            System.out.println();
            
            // 7. Process payment
            System.out.println("7. Processing Payment (Stripe):");
            PaymentMethod paymentMethod = new PaymentMethod(PaymentMethod.PaymentType.CREDIT_CARD);
            paymentMethod.setLastFourDigits("1234");
            paymentMethod.setCardBrand("Visa");
            paymentMethod.setStripePaymentMethodId("pm_stripe_12345");
            
            Payment processedPayment = paymentService.processStripePayment(session, paymentMethod, payment.getAmount());
            System.out.println("Payment status: " + processedPayment.getStatus());
            System.out.println("Transaction ID: " + processedPayment.getTransactionId());
            System.out.println();
            
            // 8. Final status
            System.out.println("8. Final Parking Area Status:");
            System.out.println(parkingArea);
            System.out.println("Available slots: " + parkingArea.getAvailableSlotCount());
        }
        
        System.out.println("\n=== Demonstration Complete ===");
    }
    
    public static void main(String[] args) {
        // Check if GUI mode is requested
        if (args.length > 0 && args[0].equalsIgnoreCase("--gui")) {
            // Launch GUI
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    com.parkomfy.gui.ParkomfyGUI gui = new com.parkomfy.gui.ParkomfyGUI();
                    gui.setVisible(true);
                } catch (Exception e) {
                    System.err.println("Error launching GUI: " + e.getMessage());
                    e.printStackTrace();
                    // Fallback to console mode
                    ParkomfyApplication app = new ParkomfyApplication();
                    app.demonstrateParkingFlow();
                }
            });
        } else {
            // Console mode (default)
            ParkomfyApplication app = new ParkomfyApplication();
            app.demonstrateParkingFlow();
        }
    }
}
