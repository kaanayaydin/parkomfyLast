package com.parkomfy.repository;

import com.parkomfy.model.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DatabaseManager implements MySQL database operations
 * Handles all database connections and queries
 */
public class DatabaseManager implements IParkingRepository {
    
    private Connection connection;
    private String url;
    private String username;
    private String password;
    private final Map<String, ParkingArea> demoAreas = new HashMap<>();
    private final List<SlotReservation> demoReservations = new ArrayList<>();
    private final Map<String, User> demoUsers = new HashMap<>();
    private final Map<String, Vehicle> demoVehicles = new HashMap<>();
    private final List<ParkingSession> demoSessions = new ArrayList<>();
    private final List<DetectionResult> demoDetections = new ArrayList<>();
    private final Map<String, List<String>> demoPushTokens = new HashMap<>();
    private final Map<String, String> demoLotKeys = new HashMap<>();
    private final Map<String, Boolean> demoCalibrated = new HashMap<>();
    private boolean useDemoData;
    
    public DatabaseManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        initializeDatabase();
        useDemoData = (connection == null);
        if (useDemoData) {
            seedDemoAreas();
            seedDemoUsers();
            System.out.println("Using in-memory demo parking data (MySQL unavailable)");
        }
    }

    /** MySQL sonradan açıldıysa demo moddan veritabanına geç. */
    private synchronized void ensureConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed() && connection.isValid(2)) {
                    return;
                }
            } catch (SQLException ignored) {
                connection = null;
            }
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, username, password);
            createTables();
            if (useDemoData) {
                System.out.println("MySQL bağlantısı kuruldu — veritabanı moduna geçildi");
            }
            useDemoData = false;
        } catch (Exception e) {
            connection = null;
            useDemoData = true;
        }
    }

    private boolean isDemoMode() {
        if (useDemoData) {
            ensureConnection();
        }
        return useDemoData;
    }
    
    private void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, username, password);
            createTables();
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void createTables() {
        if (connection == null) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS parking_areas (" +
                "area_id VARCHAR(32) PRIMARY KEY, " +
                "area_name VARCHAR(128) NOT NULL, " +
                "address VARCHAR(255) NULL)");
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS parking_slots (" +
                "slot_id VARCHAR(64) PRIMARY KEY, " +
                "area_id VARCHAR(32) NOT NULL, " +
                "floor_number INT NOT NULL DEFAULT 0, " +
                "zone_name VARCHAR(8) NOT NULL DEFAULT 'A', " +
                "slot_number INT NOT NULL, " +
                "status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE', " +
                "CONSTRAINT fk_slots_area FOREIGN KEY (area_id) REFERENCES parking_areas(area_id))");
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS slot_reservations (" +
                "reservation_id VARCHAR(64) PRIMARY KEY, " +
                "slot_id VARCHAR(64) NOT NULL, " +
                "area_id VARCHAR(32) NOT NULL, " +
                "license_plate VARCHAR(32) NOT NULL, " +
                "start_time DATETIME NOT NULL, " +
                "end_time DATETIME NOT NULL, " +
                "status VARCHAR(16) NOT NULL DEFAULT 'RESERVED', " +
                "total_fee DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "CONSTRAINT fk_res_slot FOREIGN KEY (slot_id) REFERENCES parking_slots(slot_id), " +
                "CONSTRAINT fk_res_area FOREIGN KEY (area_id) REFERENCES parking_areas(area_id))");
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "user_id VARCHAR(64) PRIMARY KEY, " +
                "email VARCHAR(128) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(128) NOT NULL, " +
                "full_name VARCHAR(128) NOT NULL, " +
                "license_plate VARCHAR(32) NULL, " +
                "role VARCHAR(16) NOT NULL DEFAULT 'USER', " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS vehicles (" +
                "vehicle_id VARCHAR(64) PRIMARY KEY, " +
                "license_plate VARCHAR(32) NOT NULL, " +
                "vehicle_type VARCHAR(16) NOT NULL DEFAULT 'CAR', " +
                "entry_time DATETIME NOT NULL, " +
                "exit_time DATETIME NULL, " +
                "user_id VARCHAR(64) NULL)");
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS parking_sessions (" +
                "session_id VARCHAR(64) PRIMARY KEY, " +
                "vehicle_id VARCHAR(64) NOT NULL, " +
                "slot_id VARCHAR(64) NOT NULL, " +
                "area_id VARCHAR(32) NOT NULL, " +
                "license_plate VARCHAR(32) NOT NULL, " +
                "entry_time DATETIME NOT NULL, " +
                "exit_time DATETIME NULL, " +
                "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE')");
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS detection_results (" +
                "detection_id VARCHAR(64) PRIMARY KEY, " +
                "camera_id VARCHAR(64) NOT NULL, " +
                "slot_id VARCHAR(64) NULL, " +
                "area_id VARCHAR(32) NULL, " +
                "detection_type VARCHAR(16) NOT NULL, " +
                "license_plate VARCHAR(32) NULL, " +
                "confidence DOUBLE NOT NULL DEFAULT 0, " +
                "vehicle_detected TINYINT(1) NOT NULL DEFAULT 0, " +
                "detected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS push_tokens (" +
                "token_id VARCHAR(64) PRIMARY KEY, " +
                "user_id VARCHAR(64) NOT NULL, " +
                "expo_push_token VARCHAR(256) NOT NULL, " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_push_token (expo_push_token))");
            migrateCalibrationColumns(stmt);
            seedMysqlData(stmt);
            System.out.println("MySQL tables ready (parkomfy)");
        } catch (SQLException e) {
            System.err.println("Failed to initialize MySQL tables: " + e.getMessage());
        }
    }

    private void seedMysqlData(Statement stmt) throws SQLException {
        stmt.executeUpdate(
            "INSERT INTO users (user_id, email, password_hash, full_name, license_plate, role) VALUES " +
            "('USR-ADMIN', 'admin', '" + com.parkomfy.service.AuthService.hashPassword("1234") + "', " +
            "'Yönetici', '34 OZU 450', 'ADMIN') " +
            "ON DUPLICATE KEY UPDATE role='ADMIN', full_name='Yönetici'");
        stmt.executeUpdate(
            "UPDATE users SET role='USER' WHERE email <> 'admin' AND role='ADMIN'");
    }

    private void seedDemoUsers() {
        User admin = new User("USR-ADMIN", "admin", null, "Yönetici");
        admin.setPasswordHash(com.parkomfy.service.AuthService.hashPassword("1234"));
        admin.setLicensePlate("34 OZU 450");
        admin.setRole("ADMIN");
        demoUsers.put("admin", admin);
    }

    private void migrateCalibrationColumns(Statement stmt) {
        String[] alters = {
            "ALTER TABLE parking_areas ADD COLUMN lot_key VARCHAR(32) NULL",
            "ALTER TABLE parking_areas ADD COLUMN calibrated TINYINT(1) NOT NULL DEFAULT 0",
            "ALTER TABLE parking_slots ADD COLUMN c1x DOUBLE NULL, ADD COLUMN c1y DOUBLE NULL",
            "ALTER TABLE parking_slots ADD COLUMN c2x DOUBLE NULL, ADD COLUMN c2y DOUBLE NULL",
            "ALTER TABLE parking_slots ADD COLUMN c3x DOUBLE NULL, ADD COLUMN c3y DOUBLE NULL",
            "ALTER TABLE parking_slots ADD COLUMN c4x DOUBLE NULL, ADD COLUMN c4y DOUBLE NULL",
        };
        for (String sql : alters) {
            try { stmt.executeUpdate(sql); } catch (SQLException ignored) { }
        }
    }

    private void seedDemoAreas() {
        // MySQL yokken veriler bellekte tutulur ve her restart'ta silinir.
        // Eren'in kalibrasyonu (grpc_server/calibrations/AREA-004.json) burada
        // tohumlanir: loop1 videosu, videodaki 3 slot (poligon koseleriyle).
        // Boylece restart sonrasi otopark eski haliyle geri gelir.
        // MySQL acilirsa (ensureConnection) bu calismaz, gercek DB kullanilir.
        ParkingArea area = new ParkingArea("AREA-004", "Otopark", "Özyeğin Üniversitesi");
        double[][][] slotCorners = {
            {{0.662, 0.408}, {0.848, 0.485}, {0.782, 0.834}, {0.475, 0.769}},
            {{0.465, 0.462}, {0.618, 0.515}, {0.402, 0.781}, {0.222, 0.692}},
            {{0.268, 0.414}, {0.448, 0.438}, {0.185, 0.71}, {0.025, 0.615}},
        };
        for (int i = 0; i < slotCorners.length; i++) {
            ParkingSlot slot = new ParkingSlot("SLOT-loop1-" + (i + 1), 0, "A", i + 1);
            double[][] c = slotCorners[i];
            slot.setC1x(c[0][0]); slot.setC1y(c[0][1]);
            slot.setC2x(c[1][0]); slot.setC2y(c[1][1]);
            slot.setC3x(c[2][0]); slot.setC3y(c[2][1]);
            slot.setC4x(c[3][0]); slot.setC4y(c[3][1]);
            area.addParkingSlot(slot);
        }
        demoAreas.put("AREA-004", area);
        demoLotKeys.put("AREA-004", "loop1");
        demoCalibrated.put("AREA-004", true);
    }

    private ParkingArea buildDemoArea(String areaId, String areaName, String lotKey, int slotCount, int occupiedCount) {
        ParkingArea area = new ParkingArea(areaId, areaName, "Özyeğin Üniversitesi");
        for (int i = 1; i <= slotCount; i++) {
            ParkingSlot slot = new ParkingSlot("SLOT-" + lotKey + "-" + i, 0, "A", i);
            if (i <= occupiedCount) {
                slot.setStatus(ParkingSlot.SlotStatus.OCCUPIED);
            }
            area.addParkingSlot(slot);
        }
        return area;
    }
    
    @Override
    public void saveArea(ParkingArea area) {
        saveAreaFull(area, null);
    }

    @Override
    public void saveAreaFull(ParkingArea area, String lotKey) {
        if (isDemoMode()) {
            demoAreas.put(area.getAreaId(), area);
            if (lotKey != null) demoLotKeys.put(area.getAreaId(), lotKey);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO parking_areas (area_id, area_name, address, lot_key, calibrated) VALUES (?,?,?,?,0) " +
                "ON DUPLICATE KEY UPDATE area_name = VALUES(area_name), address = VALUES(address), lot_key = VALUES(lot_key)")) {
            ps.setString(1, area.getAreaId());
            ps.setString(2, area.getAreaName());
            ps.setString(3, area.getAddress());
            ps.setString(4, lotKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("saveAreaFull failed: " + e.getMessage());
        }
    }

    @Override
    public String nextAreaId() {
        if (isDemoMode()) {
            return "AREA-" + String.format("%03d", demoAreas.size() + 1);
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT area_id FROM parking_areas ORDER BY area_id DESC LIMIT 1")) {
            if (rs.next()) {
                String last = rs.getString(1);
                int n = Integer.parseInt(last.replace("AREA-", ""));
                return "AREA-" + String.format("%03d", n + 1);
            }
        } catch (SQLException e) {
            System.err.println("nextAreaId failed: " + e.getMessage());
        }
        return "AREA-004";
    }

    @Override
    public String getLotKey(String areaId) {
        if (isDemoMode()) return demoLotKeys.getOrDefault(areaId, "lot" + areaId);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT lot_key FROM parking_areas WHERE area_id = ?")) {
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) return rs.getString(1);
            }
        } catch (SQLException e) {
            System.err.println("getLotKey failed: " + e.getMessage());
        }
        return areaId.toLowerCase();
    }

    @Override
    public boolean isAreaCalibrated(String areaId) {
        if (isDemoMode()) return demoCalibrated.getOrDefault(areaId, false);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT calibrated FROM parking_areas WHERE area_id = ?")) {
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            System.err.println("isAreaCalibrated failed: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void markAreaCalibrated(String areaId, boolean calibrated) {
        if (isDemoMode()) {
            demoCalibrated.put(areaId, calibrated);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE parking_areas SET calibrated = ? WHERE area_id = ?")) {
            ps.setBoolean(1, calibrated);
            ps.setString(2, areaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("markAreaCalibrated failed: " + e.getMessage());
        }
    }

    @Override
    public void deleteSlotsForArea(String areaId) {
        if (isDemoMode()) {
            ParkingArea area = demoAreas.get(areaId);
            if (area != null) area.getParkingSlots().clear();
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM parking_slots WHERE area_id = ?")) {
            ps.setString(1, areaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("deleteSlotsForArea failed: " + e.getMessage());
        }
    }

    @Override
    public void resetAllParkingData() {
        if (isDemoMode()) {
            demoAreas.clear();
            demoReservations.clear();
            demoSessions.clear();
            demoDetections.clear();
            demoLotKeys.clear();
            demoCalibrated.clear();
            demoVehicles.clear();
            return;
        }
        if (connection == null) {
            return;
        }
        String[] sqls = {
            "DELETE FROM slot_reservations",
            "DELETE FROM parking_sessions",
            "DELETE FROM detection_results",
            "DELETE FROM parking_slots",
            "DELETE FROM parking_areas"
        };
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
            for (String sql : sqls) {
                stmt.executeUpdate(sql);
            }
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
            System.out.println("All parking areas, slots and reservations cleared (users kept)");
        } catch (SQLException e) {
            System.err.println("resetAllParkingData failed: " + e.getMessage());
            throw new IllegalStateException("Otopark verileri silinemedi: " + e.getMessage());
        }
    }

    @Override
    public void insertSlot(String areaId, ParkingSlot slot) {
        if (isDemoMode()) {
            ParkingArea area = demoAreas.get(areaId);
            if (area != null) area.addParkingSlot(slot);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO parking_slots (slot_id, area_id, floor_number, zone_name, slot_number, status, " +
                "c1x, c1y, c2x, c2y, c3x, c3y, c4x, c4y) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, slot.getSlotId());
            ps.setString(2, areaId);
            ps.setInt(3, slot.getFloorNumber());
            ps.setString(4, slot.getZone());
            ps.setInt(5, slot.getSlotNumber());
            ps.setString(6, slot.getStatus().name());
            ps.setDouble(7, slot.getC1x()); ps.setDouble(8, slot.getC1y());
            ps.setDouble(9, slot.getC2x()); ps.setDouble(10, slot.getC2y());
            ps.setDouble(11, slot.getC3x()); ps.setDouble(12, slot.getC3y());
            ps.setDouble(13, slot.getC4x()); ps.setDouble(14, slot.getC4y());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("insertSlot failed: " + e.getMessage());
        }
    }
    
    @Override
    public ParkingArea getArea(String areaId) {
        if (isDemoMode()) {
            return demoAreas.get(areaId);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT area_id, area_name, address FROM parking_areas WHERE area_id = ?")) {
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ParkingArea area = new ParkingArea(
                    rs.getString("area_id"),
                    rs.getString("area_name"),
                    rs.getString("address"));
                for (ParkingSlot slot : getAllSlots(areaId)) {
                    area.addParkingSlot(slot);
                }
                return area;
            }
        } catch (SQLException e) {
            System.err.println("getArea failed: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public List<ParkingArea> getAllAreas() {
        if (isDemoMode()) {
            return new ArrayList<>(demoAreas.values());
        }
        List<ParkingArea> areas = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT area_id FROM parking_areas")) {
            while (rs.next()) {
                ParkingArea area = getArea(rs.getString("area_id"));
                if (area != null) {
                    areas.add(area);
                }
            }
        } catch (SQLException e) {
            System.err.println("getAllAreas failed: " + e.getMessage());
        }
        return areas;
    }
    
    @Override
    public void saveSlot(ParkingSlot slot) {
        // SQL INSERT/UPDATE operation
        System.out.println("Saving parking slot: " + slot.getSlotId());
    }
    
    @Override
    public void updateSlot(ParkingSlot slot) {
        if (isDemoMode()) {
            for (ParkingArea area : demoAreas.values()) {
                ParkingSlot existing = area.getSlotById(slot.getSlotId());
                if (existing != null) {
                    existing.setStatus(slot.getStatus());
                    existing.setCurrentVehicle(slot.getCurrentVehicle());
                }
            }
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE parking_slots SET status = ? WHERE slot_id = ?")) {
            ps.setString(1, slot.getStatus().name());
            ps.setString(2, slot.getSlotId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("updateSlot failed: " + e.getMessage());
        }
    }
    
    @Override
    public ParkingSlot getSlot(String slotId) {
        if (isDemoMode()) {
            for (ParkingArea area : demoAreas.values()) {
                ParkingSlot slot = area.getSlotById(slotId);
                if (slot != null) {
                    return slot;
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slot_id, floor_number, zone_name, slot_number, status, " +
                "c1x, c1y, c2x, c2y, c3x, c3y, c4x, c4y FROM parking_slots WHERE slot_id = ?")) {
            ps.setString(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSlot(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("getSlot failed: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public List<ParkingSlot> getAllSlots(String areaId) {
        if (isDemoMode()) {
            ParkingArea area = demoAreas.get(areaId);
            if (area != null) {
                return area.getParkingSlots();
            }
            return new ArrayList<>();
        }
        List<ParkingSlot> slots = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slot_id, floor_number, zone_name, slot_number, status, " +
                "c1x, c1y, c2x, c2y, c3x, c3y, c4x, c4y FROM parking_slots WHERE area_id = ? ORDER BY slot_number")) {
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slots.add(mapSlot(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("getAllSlots failed: " + e.getMessage());
        }
        return slots;
    }

    private ParkingSlot mapSlot(ResultSet rs) throws SQLException {
        ParkingSlot slot = new ParkingSlot(
            rs.getString("slot_id"),
            rs.getInt("floor_number"),
            rs.getString("zone_name"),
            rs.getInt("slot_number"));
        slot.setStatus(ParkingSlot.SlotStatus.valueOf(rs.getString("status")));
        try {
            slot.setC1x(rs.getDouble("c1x")); slot.setC1y(rs.getDouble("c1y"));
            slot.setC2x(rs.getDouble("c2x")); slot.setC2y(rs.getDouble("c2y"));
            slot.setC3x(rs.getDouble("c3x")); slot.setC3y(rs.getDouble("c3y"));
            slot.setC4x(rs.getDouble("c4x")); slot.setC4y(rs.getDouble("c4y"));
        } catch (SQLException ignored) { }
        return slot;
    }
    
    @Override
    public void saveVehicle(Vehicle vehicle) {
        if (isDemoMode()) {
            demoVehicles.put(vehicle.getVehicleId(), vehicle);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO vehicles (vehicle_id, license_plate, vehicle_type, entry_time, exit_time, user_id) " +
                "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE entry_time = VALUES(entry_time), exit_time = VALUES(exit_time)")) {
            ps.setString(1, vehicle.getVehicleId());
            ps.setString(2, normalizePlate(vehicle.getLicensePlate()));
            ps.setString(3, vehicle.getVehicleType() != null ? vehicle.getVehicleType().name() : "CAR");
            ps.setTimestamp(4, Timestamp.valueOf(vehicle.getEntryTime()));
            ps.setTimestamp(5, vehicle.getExitTime() != null ? Timestamp.valueOf(vehicle.getExitTime()) : null);
            ps.setString(6, vehicle.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("saveVehicle failed: " + e.getMessage());
        }
    }
    
    @Override
    public Vehicle getVehicle(String vehicleId) {
        if (isDemoMode()) return demoVehicles.get(vehicleId);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT vehicle_id, license_plate, vehicle_type, entry_time, exit_time, user_id FROM vehicles WHERE vehicle_id = ?")) {
            ps.setString(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapVehicle(rs);
            }
        } catch (SQLException e) {
            System.err.println("getVehicle failed: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public Vehicle getVehicleByPlate(LicensePlate licensePlate) {
        if (licensePlate == null) return null;
        String plate = normalizePlate(licensePlate);
        if (isDemoMode()) {
            return demoVehicles.values().stream()
                .filter(v -> normalizePlate(v.getLicensePlate()).equals(plate))
                .findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT vehicle_id, license_plate, vehicle_type, entry_time, exit_time, user_id FROM vehicles " +
                "WHERE license_plate = ? ORDER BY entry_time DESC LIMIT 1")) {
            ps.setString(1, plate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapVehicle(rs);
            }
        } catch (SQLException e) {
            System.err.println("getVehicleByPlate failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<Vehicle> getRecentEnteredVehicles() {
        LocalDateTime since = LocalDateTime.now().minusHours(4);
        if (isDemoMode()) {
            return demoVehicles.values().stream()
                .filter(v -> v.getEntryTime() != null && v.getEntryTime().isAfter(since))
                .collect(java.util.stream.Collectors.toList());
        }
        List<Vehicle> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT vehicle_id, license_plate, vehicle_type, entry_time, exit_time, user_id FROM vehicles " +
                "WHERE entry_time >= ? AND (exit_time IS NULL) ORDER BY entry_time DESC")) {
            ps.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapVehicle(rs));
            }
        } catch (SQLException e) {
            System.err.println("getRecentEnteredVehicles failed: " + e.getMessage());
        }
        return list;
    }
    
    @Override
    public void saveSession(ParkingSession session) {
        if (isDemoMode()) {
            demoSessions.add(session);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO parking_sessions (session_id, vehicle_id, slot_id, area_id, license_plate, entry_time, exit_time, status) " +
                "VALUES (?,?,?,?,?,?,?,?)")) {
            String areaId = resolveAreaId(session.getParkingSlot());
            ps.setString(1, session.getSessionId());
            ps.setString(2, session.getVehicle().getVehicleId());
            ps.setString(3, session.getParkingSlot().getSlotId());
            ps.setString(4, areaId != null ? areaId : "AREA-001");
            ps.setString(5, normalizePlate(session.getVehicle().getLicensePlate()));
            ps.setTimestamp(6, Timestamp.valueOf(session.getEntryTime()));
            ps.setTimestamp(7, session.getExitTime() != null ? Timestamp.valueOf(session.getExitTime()) : null);
            ps.setString(8, session.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("saveSession failed: " + e.getMessage());
        }
    }
    
    @Override
    public void updateSession(ParkingSession session) {
        if (isDemoMode()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE parking_sessions SET exit_time = ?, status = ? WHERE session_id = ?")) {
            ps.setTimestamp(1, session.getExitTime() != null ? Timestamp.valueOf(session.getExitTime()) : null);
            ps.setString(2, session.getStatus().name());
            ps.setString(3, session.getSessionId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("updateSession failed: " + e.getMessage());
        }
    }
    
    @Override
    public ParkingSession getSession(String sessionId) {
        if (isDemoMode()) {
            return demoSessions.stream().filter(s -> s.getSessionId().equals(sessionId)).findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT session_id, vehicle_id, slot_id, area_id, license_plate, entry_time, exit_time, status " +
                "FROM parking_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapSession(rs);
            }
        } catch (SQLException e) {
            System.err.println("getSession failed: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public List<ParkingSession> getActiveSessions() {
        if (isDemoMode()) {
            return demoSessions.stream().filter(ParkingSession::isActive).collect(java.util.stream.Collectors.toList());
        }
        List<ParkingSession> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT session_id, vehicle_id, slot_id, area_id, license_plate, entry_time, exit_time, status " +
                 "FROM parking_sessions WHERE status = 'ACTIVE' ORDER BY entry_time DESC")) {
            while (rs.next()) list.add(mapSession(rs));
        } catch (SQLException e) {
            System.err.println("getActiveSessions failed: " + e.getMessage());
        }
        return list;
    }

    @Override
    public ParkingSession getActiveSessionForSlot(String slotId) {
        if (isDemoMode()) {
            return demoSessions.stream()
                .filter(s -> s.isActive() && s.getParkingSlot() != null && slotId.equals(s.getParkingSlot().getSlotId()))
                .findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT session_id, vehicle_id, slot_id, area_id, license_plate, entry_time, exit_time, status " +
                "FROM parking_sessions WHERE slot_id = ? AND status = 'ACTIVE' LIMIT 1")) {
            ps.setString(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapSession(rs);
            }
        } catch (SQLException e) {
            System.err.println("getActiveSessionForSlot failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public ParkingSession getActiveSessionByPlate(String normalizedPlate) {
        if (isDemoMode()) {
            return demoSessions.stream()
                .filter(s -> s.isActive() && s.getVehicle() != null && s.getVehicle().getLicensePlate() != null
                    && normalizePlate(s.getVehicle().getLicensePlate()).equals(normalizedPlate))
                .findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT session_id, vehicle_id, slot_id, area_id, license_plate, entry_time, exit_time, status " +
                "FROM parking_sessions WHERE license_plate = ? AND status = 'ACTIVE' LIMIT 1")) {
            ps.setString(1, normalizedPlate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapSession(rs);
            }
        } catch (SQLException e) {
            System.err.println("getActiveSessionByPlate failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public ParkingSession getLeavingOrActiveSessionByPlate(String normalizedPlate) {
        if (useDemoData) {
            return demoSessions.stream()
                .filter(s -> s.isActiveOrLeaving() && s.getVehicle() != null && s.getVehicle().getLicensePlate() != null
                    && normalizePlate(s.getVehicle().getLicensePlate()).equals(normalizedPlate))
                .findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT session_id, vehicle_id, slot_id, area_id, license_plate, entry_time, exit_time, status " +
                "FROM parking_sessions WHERE license_plate = ? AND status IN ('ACTIVE','LEAVING') " +
                "ORDER BY CASE status WHEN 'LEAVING' THEN 0 ELSE 1 END LIMIT 1")) {
            ps.setString(1, normalizedPlate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapSession(rs);
            }
        } catch (SQLException e) {
            System.err.println("getLeavingOrActiveSessionByPlate failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<ParkingSession> getLeavingSessions() {
        if (useDemoData) {
            return demoSessions.stream()
                .filter(ParkingSession::isLeaving)
                .collect(java.util.stream.Collectors.toList());
        }
        List<ParkingSession> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT session_id, vehicle_id, slot_id, area_id, license_plate, entry_time, exit_time, status " +
                 "FROM parking_sessions WHERE status = 'LEAVING' ORDER BY entry_time DESC")) {
            while (rs.next()) list.add(mapSession(rs));
        } catch (SQLException e) {
            System.err.println("getLeavingSessions failed: " + e.getMessage());
        }
        return list;
    }
    
    @Override
    public void savePayment(Payment payment) {
        System.out.println("Saving payment: " + payment.getPaymentId() + " - Amount: " + payment.getAmount());
    }
    
    @Override
    public void updatePayment(Payment payment) {
        System.out.println("Updating payment: " + payment.getPaymentId());
    }
    
    @Override
    public Payment getPayment(String paymentId) {
        return null;
    }
    
    @Override
    public void saveDetectionResult(DetectionResult result) {
        if (isDemoMode()) {
            demoDetections.add(0, result);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO detection_results (detection_id, camera_id, slot_id, area_id, detection_type, " +
                "license_plate, confidence, vehicle_detected, detected_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            String areaId = null;
            if (result.getSlotId() != null) {
                ParkingSlot slot = getSlot(result.getSlotId());
                if (slot != null) areaId = resolveAreaId(slot);
            }
            ps.setString(1, result.getDetectionId());
            ps.setString(2, result.getCameraId());
            ps.setString(3, result.getSlotId());
            ps.setString(4, areaId);
            ps.setString(5, result.getDetectionType() != null ? result.getDetectionType().name() : "OCCUPANCY");
            ps.setString(6, result.getLicensePlateText());
            ps.setDouble(7, result.getConfidence());
            ps.setBoolean(8, result.isVehicleDetected());
            ps.setTimestamp(9, Timestamp.valueOf(result.getDetectionTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("saveDetectionResult failed: " + e.getMessage());
        }
    }
    
    @Override
    public List<DetectionResult> getDetectionResults(String slotId) {
        return getRecentDetections(null, 50).stream()
            .filter(d -> slotId == null || slotId.equals(d.getSlotId()))
            .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<DetectionResult> getRecentDetections(String areaId, int limit) {
        if (isDemoMode()) {
            return demoDetections.stream().limit(limit).collect(java.util.stream.Collectors.toList());
        }
        List<DetectionResult> list = new ArrayList<>();
        String sql = areaId != null
            ? "SELECT detection_id, camera_id, slot_id, detection_type, license_plate, confidence, vehicle_detected, detected_at " +
              "FROM detection_results WHERE slot_id IN (SELECT slot_id FROM parking_slots WHERE area_id = ?) " +
              "ORDER BY detected_at DESC LIMIT ?"
            : "SELECT detection_id, camera_id, slot_id, detection_type, license_plate, confidence, vehicle_detected, detected_at " +
              "FROM detection_results ORDER BY detected_at DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (areaId != null) {
                ps.setString(1, areaId);
                ps.setInt(2, limit);
            } else {
                ps.setInt(1, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapDetection(rs));
            }
        } catch (SQLException e) {
            System.err.println("getRecentDetections failed: " + e.getMessage());
        }
        return list;
    }

    @Override
    public void savePushToken(String userId, String expoPushToken) {
        if (isDemoMode()) {
            demoPushTokens.computeIfAbsent(userId, k -> new ArrayList<>()).add(expoPushToken);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO push_tokens (token_id, user_id, expo_push_token) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)")) {
            ps.setString(1, "PT-" + System.currentTimeMillis());
            ps.setString(2, userId);
            ps.setString(3, expoPushToken);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("savePushToken failed: " + e.getMessage());
        }
    }

    @Override
    public List<String> getPushTokensForUser(String userId) {
        if (isDemoMode()) {
            return demoPushTokens.getOrDefault(userId, new ArrayList<>());
        }
        List<String> tokens = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT expo_push_token FROM push_tokens WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tokens.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("getPushTokensForUser failed: " + e.getMessage());
        }
        return tokens;
    }

    @Override
    public List<String> getPushTokensForPlate(String normalizedPlate) {
        if (isDemoMode()) {
            User user = demoUsers.values().stream()
                .filter(u -> u.getLicensePlate() != null
                    && normalizePlate(new LicensePlate(u.getLicensePlate())).equals(normalizedPlate))
                .findFirst().orElse(null);
            if (user == null) return new ArrayList<>();
            return demoPushTokens.getOrDefault(user.getUserId(), new ArrayList<>());
        }
        List<String> tokens = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pt.expo_push_token FROM push_tokens pt JOIN users u ON pt.user_id = u.user_id " +
                "WHERE REPLACE(REPLACE(UPPER(u.license_plate), ' ', ''), '-', '') = ?")) {
            ps.setString(1, normalizedPlate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tokens.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("getPushTokensForPlate failed: " + e.getMessage());
        }
        return tokens;
    }
    
    @Override
    public void saveUser(User user) {
        if (isDemoMode()) {
            demoUsers.put(user.getEmail().toLowerCase(), user);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (user_id, email, password_hash, full_name, license_plate, role) " +
                "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getEmail().toLowerCase());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getLicensePlate());
            ps.setString(6, user.getRole() != null ? user.getRole() : "USER");
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("saveUser failed: " + e.getMessage());
            throw new IllegalStateException("Kullanıcı kaydedilemedi: " + e.getMessage());
        }
    }
    
    @Override
    public User getUser(String userId) {
        if (isDemoMode()) {
            return demoUsers.values().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT user_id, email, password_hash, full_name, license_plate, role FROM users WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("getUser failed: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public User getUserByEmail(String email) {
        String key = email.trim().toLowerCase();
        if (isDemoMode()) {
            return demoUsers.get(key);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT user_id, email, password_hash, full_name, license_plate, role FROM users WHERE email = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("getUserByEmail failed: " + e.getMessage());
        }
        return null;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User(
            rs.getString("user_id"),
            rs.getString("email"),
            null,
            rs.getString("full_name"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setLicensePlate(rs.getString("license_plate"));
        user.setRole(rs.getString("role"));
        return user;
    }

    @Override
    public void saveReservation(SlotReservation reservation) {
        if (isDemoMode()) {
            demoReservations.add(reservation);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO slot_reservations (reservation_id, slot_id, area_id, license_plate, " +
                "start_time, end_time, status, total_fee, created_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, reservation.getReservationId());
            ps.setString(2, reservation.getSlotId());
            ps.setString(3, reservation.getAreaId());
            ps.setString(4, reservation.getLicensePlate());
            ps.setTimestamp(5, Timestamp.valueOf(reservation.getStartTime()));
            ps.setTimestamp(6, Timestamp.valueOf(reservation.getEndTime()));
            ps.setString(7, reservation.getStatus().name());
            ps.setDouble(8, reservation.getTotalFee());
            ps.setTimestamp(9, Timestamp.valueOf(reservation.getCreatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("saveReservation failed: " + e.getMessage());
        }
    }

    @Override
    public SlotReservation getReservation(String reservationId) {
        if (isDemoMode()) {
            return demoReservations.stream()
                .filter(r -> r.getReservationId().equals(reservationId))
                .findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reservation_id, slot_id, area_id, license_plate, start_time, end_time, status, total_fee, created_at " +
                "FROM slot_reservations WHERE reservation_id = ?")) {
            ps.setString(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapReservation(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("getReservation failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<SlotReservation> getReservationsByPlate(String licensePlate) {
        if (isDemoMode()) {
            return demoReservations.stream()
                .filter(r -> r.getLicensePlate().equalsIgnoreCase(licensePlate))
                .collect(java.util.stream.Collectors.toList());
        }
        List<SlotReservation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reservation_id, slot_id, area_id, license_plate, start_time, end_time, status, total_fee, created_at " +
                "FROM slot_reservations WHERE license_plate = ? ORDER BY start_time DESC")) {
            ps.setString(1, licensePlate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapReservation(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("getReservationsByPlate failed: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<SlotReservation> getOverlappingReservations(String slotId, LocalDateTime start, LocalDateTime end) {
        if (isDemoMode()) {
            return demoReservations.stream()
                .filter(r -> r.getSlotId().equals(slotId))
                .filter(r -> r.getStatus() == SlotReservation.ReservationStatus.RESERVED
                    || r.getStatus() == SlotReservation.ReservationStatus.ACTIVE)
                .filter(r -> r.overlaps(start, end))
                .collect(java.util.stream.Collectors.toList());
        }
        List<SlotReservation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reservation_id, slot_id, area_id, license_plate, start_time, end_time, status, total_fee, created_at " +
                "FROM slot_reservations WHERE slot_id = ? AND status IN ('RESERVED','ACTIVE') " +
                "AND start_time < ? AND end_time > ?")) {
            ps.setString(1, slotId);
            ps.setTimestamp(2, Timestamp.valueOf(end));
            ps.setTimestamp(3, Timestamp.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapReservation(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("getOverlappingReservations failed: " + e.getMessage());
        }
        return list;
    }

    private SlotReservation mapReservation(ResultSet rs) throws SQLException {
        SlotReservation r = new SlotReservation(
            rs.getString("slot_id"),
            rs.getString("area_id"),
            rs.getString("license_plate"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getDouble("total_fee"));
        r.setReservationId(rs.getString("reservation_id"));
        r.setStatus(SlotReservation.ReservationStatus.valueOf(rs.getString("status")));
        if (rs.getTimestamp("created_at") != null) {
            r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        return r;
    }

    @Override
    public void updateReservation(SlotReservation reservation) {
        if (isDemoMode()) {
            demoReservations.stream()
                .filter(r -> r.getReservationId().equals(reservation.getReservationId()))
                .findFirst()
                .ifPresent(r -> r.setStatus(reservation.getStatus()));
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE slot_reservations SET status = ? WHERE reservation_id = ?")) {
            ps.setString(1, reservation.getStatus().name());
            ps.setString(2, reservation.getReservationId());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("updateReservation failed: " + e.getMessage());
        }
    }

    @Override
    public List<SlotReservation> getAllReservations() {
        if (isDemoMode()) {
            return new ArrayList<>(demoReservations);
        }
        List<SlotReservation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reservation_id, slot_id, area_id, license_plate, start_time, end_time, status, total_fee, created_at " +
                "FROM slot_reservations ORDER BY start_time DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapReservation(rs));
            }
        } catch (SQLException e) {
            System.err.println("getAllReservations failed: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<SlotReservation> getReservationsForArea(String areaId) {
        if (isDemoMode()) {
            return demoReservations.stream()
                .filter(r -> areaId.equals(r.getAreaId()))
                .collect(java.util.stream.Collectors.toList());
        }
        List<SlotReservation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reservation_id, slot_id, area_id, license_plate, start_time, end_time, status, total_fee, created_at " +
                "FROM slot_reservations WHERE area_id = ? ORDER BY start_time DESC")) {
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapReservation(rs));
            }
        } catch (SQLException e) {
            System.err.println("getReservationsForArea failed: " + e.getMessage());
        }
        return list;
    }

    @Override
    public SlotReservation getActiveReservationByPlate(String licensePlate, LocalDateTime at) {
        String plate = licensePlate != null ? licensePlate.replaceAll("[^A-Za-z0-9]", "").toUpperCase() : "";
        if (plate.isEmpty()) return null;
        if (isDemoMode()) {
            return demoReservations.stream()
                .filter(r -> normalizePlate(new LicensePlate(r.getLicensePlate())).equals(plate))
                .filter(r -> r.getStatus() == SlotReservation.ReservationStatus.RESERVED
                    || r.getStatus() == SlotReservation.ReservationStatus.ACTIVE)
                .filter(r -> !at.isBefore(r.getStartTime()) && at.isBefore(r.getEndTime()))
                .findFirst().orElse(null);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reservation_id, slot_id, area_id, license_plate, start_time, end_time, status, total_fee, created_at " +
                "FROM slot_reservations WHERE REPLACE(REPLACE(UPPER(license_plate), ' ', ''), '-', '') = ? " +
                "AND status IN ('RESERVED','ACTIVE') AND start_time <= ? AND end_time > ? LIMIT 1")) {
            ps.setString(1, plate);
            ps.setTimestamp(2, Timestamp.valueOf(at));
            ps.setTimestamp(3, Timestamp.valueOf(at));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapReservation(rs);
            }
        } catch (SQLException e) {
            System.err.println("getActiveReservationByPlate failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<SlotReservation> getUpcomingReservations(LocalDateTime from, LocalDateTime to) {
        if (isDemoMode()) {
            return demoReservations.stream()
                .filter(r -> !r.getStartTime().isBefore(from) && !r.getStartTime().isAfter(to))
                .collect(java.util.stream.Collectors.toList());
        }
        List<SlotReservation> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT reservation_id, slot_id, area_id, license_plate, start_time, end_time, status, total_fee, created_at " +
                "FROM slot_reservations WHERE status = 'RESERVED' AND start_time >= ? AND start_time <= ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapReservation(rs));
            }
        } catch (SQLException e) {
            System.err.println("getUpcomingReservations failed: " + e.getMessage());
        }
        return list;
    }

    private Vehicle mapVehicle(ResultSet rs) throws SQLException {
        LicensePlate lp = new LicensePlate(rs.getString("license_plate"));
        Vehicle v = new Vehicle(lp, Vehicle.VehicleType.valueOf(rs.getString("vehicle_type")));
        v.setVehicleId(rs.getString("vehicle_id"));
        v.setEntryTime(rs.getTimestamp("entry_time").toLocalDateTime());
        if (rs.getTimestamp("exit_time") != null) {
            v.setExitTime(rs.getTimestamp("exit_time").toLocalDateTime());
        }
        v.setUserId(rs.getString("user_id"));
        return v;
    }

    private ParkingSession mapSession(ResultSet rs) throws SQLException {
        Vehicle vehicle = getVehicle(rs.getString("vehicle_id"));
        if (vehicle == null) {
            vehicle = new Vehicle(new LicensePlate(rs.getString("license_plate")));
        }
        ParkingSlot slot = getSlot(rs.getString("slot_id"));
        if (slot == null) {
            slot = new ParkingSlot(rs.getString("slot_id"), 0, "A", 1);
        }
        ParkingSession session = new ParkingSession(vehicle, slot);
        session.setSessionId(rs.getString("session_id"));
        session.setEntryTime(rs.getTimestamp("entry_time").toLocalDateTime());
        session.setStatus(ParkingSession.SessionStatus.valueOf(rs.getString("status")));
        if (rs.getTimestamp("exit_time") != null) {
            session.setExitTime(rs.getTimestamp("exit_time").toLocalDateTime());
        }
        return session;
    }

    private DetectionResult mapDetection(ResultSet rs) throws SQLException {
        DetectionResult d = new DetectionResult(
            rs.getString("camera_id"),
            rs.getString("slot_id"),
            rs.getBoolean("vehicle_detected"),
            rs.getDouble("confidence"));
        d.setDetectionId(rs.getString("detection_id"));
        d.setLicensePlateText(rs.getString("license_plate"));
        d.setDetectionType(DetectionResult.DetectionType.valueOf(rs.getString("detection_type")));
        d.setDetectionTime(rs.getTimestamp("detected_at").toLocalDateTime());
        return d;
    }

    private String normalizePlate(LicensePlate lp) {
        if (lp == null || lp.getPlateNumber() == null) return "";
        return lp.getPlateNumber().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String resolveAreaId(ParkingSlot slot) {
        if (slot == null) return null;
        String slotId = slot.getSlotId();
        if (slotId.contains("istasyon1")) return "AREA-001";
        if (slotId.contains("istasyon2")) return "AREA-002";
        if (slotId.contains("istasyon3")) return "AREA-003";
        return null;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}
