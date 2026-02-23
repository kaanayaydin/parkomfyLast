package com.parkomfy.gui;

import com.parkomfy.model.*;
import com.parkomfy.repository.DatabaseManager;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.service.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PARKOMFY GUI Application
 * Simple Swing-based graphical user interface
 */
public class ParkomfyGUI extends JFrame {
    
    private IParkingRepository repository;
    private IParkingService parkingService;
    private IPaymentService paymentService;
    private IDetectionService detectionService;
    private ParkingArea parkingArea;
    
    // GUI Components
    private JLabel statusLabel;
    private JTextArea infoArea;
    private JList<ParkingSlot> slotList;
    private DefaultListModel<ParkingSlot> slotListModel;
    private JLabel availableCountLabel;
    private JLabel occupiedCountLabel;
    private JLabel occupancyRateLabel;
    private JTextField plateTextField;
    private ParkingSession currentSession;
    
    public ParkomfyGUI() {
        initializeServices();
        initializeGUI();
        setupEventHandlers();
        updateDisplay();
    }
    
    private void initializeServices() {
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
        PricingPolicy policy = new PricingPolicy("POLICY-001", 15.0);
        policy.setFirstHourRate(10.0);
        policy.setFreeMinutes(15);
        policy.setMaxDailyRate(100.0);
        parkingArea.setPricingPolicy(policy);
        
        // Create parking slots
        for (int i = 1; i <= 10; i++) {
            ParkingSlot slot = new ParkingSlot("SLOT-1A-" + i, 1, "A", i, i * 10.0, 10.0);
            parkingArea.addParkingSlot(slot);
        }
        
        for (int i = 1; i <= 10; i++) {
            ParkingSlot slot = new ParkingSlot("SLOT-1B-" + i, 1, "B", i, i * 10.0, 20.0);
            parkingArea.addParkingSlot(slot);
        }
        
        // Create cameras
        Camera entranceCamera = new Camera("CAM-ENTRANCE-001", "Entrance LPR Camera",
                Camera.CameraType.ENTRANCE_LPR, "Main Entrance");
        entranceCamera.setHasNightVision(true);
        parkingArea.addCamera(entranceCamera);
    }
    
    private void initializeGUI() {
        setTitle("PARKOMFY - Smart Parking Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        
        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - Status
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - Split into left (slots) and right (info)
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setLeftComponent(createSlotPanel());
        centerSplit.setRightComponent(createInfoPanel());
        centerSplit.setDividerLocation(400);
        mainPanel.add(centerSplit, BorderLayout.CENTER);
        
        // Bottom panel - Controls
        JPanel bottomPanel = createControlPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Parking Area Status",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        availableCountLabel = new JLabel("Available: 0");
        availableCountLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        availableCountLabel.setForeground(new Color(0, 150, 0));
        
        occupiedCountLabel = new JLabel("Occupied: 0");
        occupiedCountLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        occupiedCountLabel.setForeground(new Color(200, 0, 0));
        
        occupancyRateLabel = new JLabel("Occupancy: 0%");
        occupancyRateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        occupancyRateLabel.setForeground(new Color(0, 0, 150));
        
        statsPanel.add(availableCountLabel);
        statsPanel.add(occupiedCountLabel);
        statsPanel.add(occupancyRateLabel);
        
        statusLabel = new JLabel("System Ready");
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        
        panel.add(statsPanel, BorderLayout.WEST);
        panel.add(statusLabel, BorderLayout.EAST);
        
        return panel;
    }
    
    private JPanel createSlotPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Parking Slots",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        slotListModel = new DefaultListModel<>();
        slotList = new JList<>(slotListModel);
        slotList.setCellRenderer(new SlotListCellRenderer());
        slotList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JScrollPane scrollPane = new JScrollPane(slotList);
        scrollPane.setPreferredSize(new Dimension(380, 400));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "System Information",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        infoArea.setBackground(new Color(245, 245, 245));
        
        JScrollPane scrollPane = new JScrollPane(infoArea);
        scrollPane.setPreferredSize(new Dimension(550, 400));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Vehicle Control",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Left side - Entry controls
        JPanel entryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        entryPanel.setBorder(BorderFactory.createTitledBorder("Vehicle Entry"));
        
        JLabel plateLabel = new JLabel("License Plate:");
        plateTextField = new JTextField(12);
        plateTextField.setToolTipText("Enter license plate (e.g., 34ABC123)");
        
        JButton entryButton = new JButton("Enter Parking");
        entryButton.setBackground(new Color(0, 150, 0));
        entryButton.setForeground(Color.WHITE);
        entryButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        entryButton.addActionListener(e -> handleVehicleEntry());
        
        entryPanel.add(plateLabel);
        entryPanel.add(plateTextField);
        entryPanel.add(entryButton);
        
        // Right side - Exit controls
        JPanel exitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        exitPanel.setBorder(BorderFactory.createTitledBorder("Vehicle Exit"));
        
        JButton exitButton = new JButton("Exit & Pay");
        exitButton.setBackground(new Color(200, 0, 0));
        exitButton.setForeground(Color.WHITE);
        exitButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        exitButton.addActionListener(e -> handleVehicleExit());
        
        JButton refreshButton = new JButton("Refresh Status");
        refreshButton.addActionListener(e -> updateDisplay());
        
        JButton detectButton = new JButton("Run Detection");
        detectButton.setBackground(new Color(0, 100, 200));
        detectButton.setForeground(Color.WHITE);
        detectButton.addActionListener(e -> runDetection());
        
        exitPanel.add(exitButton);
        exitPanel.add(refreshButton);
        exitPanel.add(detectButton);
        
        panel.add(entryPanel, BorderLayout.WEST);
        panel.add(exitPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void setupEventHandlers() {
        // Slot selection
        slotList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ParkingSlot selected = slotList.getSelectedValue();
                if (selected != null) {
                    showSlotInfo(selected);
                }
            }
        });
    }
    
    private void handleVehicleEntry() {
        String plateText = plateTextField.getText().trim();
        if (plateText.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please enter a license plate number!", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            // Detect license plate (simulate)
            LicensePlate plate = new LicensePlate(plateText);
            if (!plate.isValid()) {
                JOptionPane.showMessageDialog(this,
                    "Invalid license plate format!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Create vehicle
            Vehicle vehicle = new Vehicle(plate, Vehicle.VehicleType.CAR);
            vehicle.setEntryTime(LocalDateTime.now());
            
            // Find available slot
            List<ParkingSlot> availableSlots = parkingService.findAvailableSlots(parkingArea);
            if (availableSlots.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "No available parking slots!",
                    "Full",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Occupy slot
            ParkingSlot selectedSlot = availableSlots.get(0);
            currentSession = parkingService.occupySlot(selectedSlot, vehicle);
            
            // Update display
            updateDisplay();
            appendInfo("Vehicle Entry: " + plate + " → " + selectedSlot.getLocationString());
            appendInfo("Session ID: " + currentSession.getSessionId());
            appendInfo("Entry Time: " + vehicle.getEntryTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            appendInfo("---");
            
            statusLabel.setText("Vehicle entered: " + plate);
            plateTextField.setText("");
            
            JOptionPane.showMessageDialog(this,
                "Vehicle " + plate + " parked at " + selectedSlot.getLocationString(),
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void handleVehicleExit() {
        // Find occupied slot (active session)
        ParkingSlot occupiedSlot = null;
        for (ParkingSlot slot : parkingArea.getParkingSlots()) {
            if (slot.isOccupied() && slot.getCurrentVehicle() != null) {
                occupiedSlot = slot;
                break;
            }
        }
        
        if (occupiedSlot == null || occupiedSlot.getCurrentVehicle() == null) {
            JOptionPane.showMessageDialog(this,
                "No active parking session found!\nPlease enter a vehicle first.",
                "No Session",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            Vehicle vehicle = occupiedSlot.getCurrentVehicle();
            
            // If entry time was just set, simulate a parking duration (2 hours 30 minutes)
            if (vehicle.getEntryTime().isAfter(LocalDateTime.now().minusMinutes(5))) {
                // Recently entered, simulate longer parking for demo
                vehicle.setEntryTime(LocalDateTime.now().minusHours(2).minusMinutes(30));
                appendInfo("Note: Simulating 2.5 hours parking for demonstration");
            }
            
            // Find or create session
            if (currentSession == null || !currentSession.isActive()) {
                currentSession = new ParkingSession(vehicle, occupiedSlot);
                currentSession.getVehicle().setEntryTime(vehicle.getEntryTime());
            }
            
            // Complete session
            Payment payment = parkingService.completeSession(currentSession);
            long durationMinutes = currentSession.getDurationMinutes();
            double fee = payment.getAmount();
            
            // Process payment
            PaymentMethod paymentMethod = new PaymentMethod(PaymentMethod.PaymentType.CREDIT_CARD);
            paymentMethod.setLastFourDigits("1234");
            paymentMethod.setCardBrand("Visa");
            paymentMethod.setStripePaymentMethodId("pm_stripe_demo");
            
            Payment processedPayment = paymentService.processStripePayment(
                currentSession, paymentMethod, fee);
            
            // Update display
            updateDisplay();
            appendInfo("Vehicle Exit: " + vehicle.getLicensePlate());
            appendInfo("Duration: " + durationMinutes + " minutes (" + 
                      String.format("%.1f", durationMinutes / 60.0) + " hours)");
            appendInfo("Fee: " + String.format("%.2f", fee) + " " + 
                      parkingArea.getPricingPolicy().getCurrency());
            appendInfo("Payment Status: " + processedPayment.getStatus());
            if (processedPayment.getTransactionId() != null) {
                appendInfo("Transaction ID: " + processedPayment.getTransactionId());
            }
            appendInfo("---");
            
            statusLabel.setText("Payment processed: " + String.format("%.2f", fee) + " TRY");
            
            // Show payment dialog
            String message = String.format(
                "Parking Fee: %.2f %s\n" +
                "Duration: %d minutes (%.1f hours)\n" +
                "Payment Status: %s",
                fee,
                parkingArea.getPricingPolicy().getCurrency(),
                durationMinutes,
                durationMinutes / 60.0,
                processedPayment.getStatus()
            );
            
            if (processedPayment.getTransactionId() != null) {
                message += "\nTransaction ID: " + processedPayment.getTransactionId();
            }
            
            JOptionPane.showMessageDialog(this,
                message,
                "Payment Complete",
                JOptionPane.INFORMATION_MESSAGE);
            
            currentSession = null;
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            appendInfo("ERROR: " + e.getMessage());
        }
    }
    
    private void runDetection() {
        appendInfo("Running YOLO Detection...");
        statusLabel.setText("Detection in progress...");
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                Camera areaCamera = parkingArea.getCameras().stream()
                    .filter(c -> c.getType() == Camera.CameraType.PARKING_AREA)
                    .findFirst()
                    .orElse(null);
                
                if (areaCamera == null) {
                    // Create a camera for detection
                    areaCamera = new Camera("CAM-AREA-001", "Parking Area Camera",
                            Camera.CameraType.PARKING_AREA, "Floor 1");
                    parkingArea.addCamera(areaCamera);
                }
                
                List<ParkingSlot> slots = parkingArea.getParkingSlots();
                int detected = 0;
                
                for (ParkingSlot slot : slots) {
                    DetectionResult result = detectionService.detectOccupancy(areaCamera, slot);
                    detectionService.processDetectionResult(result);
                    
                    if (result.isVehicleDetected()) {
                        detected++;
                        publish("Detected vehicle at " + slot.getSlotId() + 
                               " (confidence: " + String.format("%.1f", result.getConfidence() * 100) + "%)");
                    }
                }
                
                publish("Detection complete: " + detected + " vehicles detected");
                publish("Detection accuracy: " + 
                       String.format("%.2f", detectionService.getDetectionAccuracy()) + "%");
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendInfo(chunk);
                }
            }
            
            @Override
            protected void done() {
                updateDisplay();
                statusLabel.setText("Detection complete");
            }
        };
        
        worker.execute();
    }
    
    private void updateDisplay() {
        // Update statistics
        int available = parkingArea.getAvailableSlotCount();
        int occupied = parkingArea.getOccupiedSlotCount();
        double occupancyRate = parkingArea.getOccupancyRate();
        
        availableCountLabel.setText("Available: " + available);
        occupiedCountLabel.setText("Occupied: " + occupied);
        occupancyRateLabel.setText(String.format("Occupancy: %.2f%%", occupancyRate));
        
        // Update slot list
        slotListModel.clear();
        List<ParkingSlot> slots = parkingArea.getParkingSlots();
        for (ParkingSlot slot : slots) {
            slotListModel.addElement(slot);
        }
    }
    
    private void showSlotInfo(ParkingSlot slot) {
        StringBuilder info = new StringBuilder();
        info.append("Slot ID: ").append(slot.getSlotId()).append("\n");
        info.append("Location: ").append(slot.getLocationString()).append("\n");
        info.append("Status: ").append(slot.getStatus()).append("\n");
        info.append("Last Update: ").append(
            slot.getLastStatusUpdate().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        ).append("\n");
        
        if (slot.getCurrentVehicle() != null) {
            info.append("Vehicle: ").append(slot.getCurrentVehicle().getLicensePlate()).append("\n");
            info.append("Entry Time: ").append(
                slot.getCurrentVehicle().getEntryTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            ).append("\n");
        }
        
        JOptionPane.showMessageDialog(this, info.toString(), "Slot Information", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void appendInfo(String text) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        infoArea.append("[" + timestamp + "] " + text + "\n");
        infoArea.setCaretPosition(infoArea.getDocument().getLength());
    }
    
    /**
     * Custom cell renderer for parking slots list
     */
    private static class SlotListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, 
                                                     int index, boolean isSelected, 
                                                     boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ParkingSlot) {
                ParkingSlot slot = (ParkingSlot) value;
                String text = slot.getLocationString() + " - " + slot.getStatus();
                setText(text);
                
                // Color coding
                if (slot.isAvailable()) {
                    setBackground(isSelected ? new Color(200, 255, 200) : new Color(240, 255, 240));
                    setForeground(new Color(0, 150, 0));
                } else if (slot.isOccupied()) {
                    setBackground(isSelected ? new Color(255, 200, 200) : new Color(255, 240, 240));
                    setForeground(new Color(200, 0, 0));
                } else {
                    setBackground(isSelected ? new Color(200, 200, 200) : Color.WHITE);
                }
            }
            
            return this;
        }
    }
    
    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Run GUI on EDT
        SwingUtilities.invokeLater(() -> {
            ParkomfyGUI gui = new ParkomfyGUI();
            gui.setVisible(true);
            gui.appendInfo("=== PARKOMFY System Started ===");
            gui.appendInfo("Welcome to Smart Parking Management System");
            gui.appendInfo("Total Capacity: " + gui.parkingArea.getTotalCapacity() + " slots");
            gui.appendInfo("---");
        });
    }
}

