CREATE DATABASE IF NOT EXISTS parkomfy
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE parkomfy;

CREATE TABLE IF NOT EXISTS parking_areas (
    area_id     VARCHAR(32) PRIMARY KEY,
    area_name   VARCHAR(128) NOT NULL,
    address     VARCHAR(255) NULL,
    lot_key     VARCHAR(32) NULL,
    calibrated  TINYINT(1) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS parking_slots (
    slot_id      VARCHAR(64) PRIMARY KEY,
    area_id      VARCHAR(32) NOT NULL,
    floor_number INT NOT NULL DEFAULT 0,
    zone_name    VARCHAR(8) NOT NULL DEFAULT 'A',
    slot_number  INT NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    c1x DOUBLE NULL, c1y DOUBLE NULL,
    c2x DOUBLE NULL, c2y DOUBLE NULL,
    c3x DOUBLE NULL, c3y DOUBLE NULL,
    c4x DOUBLE NULL, c4y DOUBLE NULL,
    CONSTRAINT fk_slots_area FOREIGN KEY (area_id) REFERENCES parking_areas(area_id)
);

INSERT INTO parking_areas (area_id, area_name, address) VALUES
    ('AREA-001', 'A Blok Otoparkı', 'Özyeğin Üniversitesi'),
    ('AREA-002', 'B Blok Otoparkı', 'Özyeğin Üniversitesi'),
    ('AREA-003', 'C Blok Otoparkı', 'Özyeğin Üniversitesi')
ON DUPLICATE KEY UPDATE area_name = VALUES(area_name);

INSERT INTO parking_slots (slot_id, area_id, floor_number, zone_name, slot_number, status) VALUES
    ('SLOT-istasyon1-1', 'AREA-001', 0, 'A', 1, 'OCCUPIED'),
    ('SLOT-istasyon1-2', 'AREA-001', 0, 'A', 2, 'OCCUPIED'),
    ('SLOT-istasyon1-3', 'AREA-001', 0, 'A', 3, 'AVAILABLE'),
    ('SLOT-istasyon1-4', 'AREA-001', 0, 'A', 4, 'AVAILABLE'),
    ('SLOT-istasyon1-5', 'AREA-001', 0, 'A', 5, 'AVAILABLE'),
    ('SLOT-istasyon2-1', 'AREA-002', 0, 'A', 1, 'OCCUPIED'),
    ('SLOT-istasyon2-2', 'AREA-002', 0, 'A', 2, 'AVAILABLE'),
    ('SLOT-istasyon2-3', 'AREA-002', 0, 'A', 3, 'AVAILABLE'),
    ('SLOT-istasyon2-4', 'AREA-002', 0, 'A', 4, 'AVAILABLE'),
    ('SLOT-istasyon3-1', 'AREA-003', 0, 'A', 1, 'OCCUPIED'),
    ('SLOT-istasyon3-2', 'AREA-003', 0, 'A', 2, 'AVAILABLE'),
    ('SLOT-istasyon3-3', 'AREA-003', 0, 'A', 3, 'AVAILABLE')
ON DUPLICATE KEY UPDATE status = VALUES(status);

CREATE TABLE IF NOT EXISTS users (
    user_id        VARCHAR(64) PRIMARY KEY,
    email          VARCHAR(128) NOT NULL UNIQUE,
    password_hash  VARCHAR(128) NOT NULL,
    full_name      VARCHAR(128) NOT NULL,
    license_plate  VARCHAR(32) NULL,
    role           VARCHAR(16) NOT NULL DEFAULT 'USER',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id    VARCHAR(64) PRIMARY KEY,
    license_plate VARCHAR(32) NOT NULL,
    vehicle_type  VARCHAR(16) NOT NULL DEFAULT 'CAR',
    entry_time    DATETIME NOT NULL,
    exit_time     DATETIME NULL,
    user_id       VARCHAR(64) NULL
);

CREATE TABLE IF NOT EXISTS parking_sessions (
    session_id    VARCHAR(64) PRIMARY KEY,
    vehicle_id    VARCHAR(64) NOT NULL,
    slot_id       VARCHAR(64) NOT NULL,
    area_id       VARCHAR(32) NOT NULL,
    license_plate VARCHAR(32) NOT NULL,
    entry_time    DATETIME NOT NULL,
    exit_time     DATETIME NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS detection_results (
    detection_id     VARCHAR(64) PRIMARY KEY,
    camera_id        VARCHAR(64) NOT NULL,
    slot_id          VARCHAR(64) NULL,
    area_id          VARCHAR(32) NULL,
    detection_type   VARCHAR(16) NOT NULL,
    license_plate    VARCHAR(32) NULL,
    confidence       DOUBLE NOT NULL DEFAULT 0,
    vehicle_detected TINYINT(1) NOT NULL DEFAULT 0,
    detected_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS push_tokens (
    token_id         VARCHAR(64) PRIMARY KEY,
    user_id          VARCHAR(64) NOT NULL,
    expo_push_token  VARCHAR(256) NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_push_token (expo_push_token)
);

CREATE TABLE IF NOT EXISTS slot_reservations (
    reservation_id VARCHAR(64) PRIMARY KEY,
    slot_id        VARCHAR(64) NOT NULL,
    area_id        VARCHAR(32) NOT NULL,
    license_plate  VARCHAR(32) NOT NULL,
    start_time     DATETIME NOT NULL,
    end_time       DATETIME NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    total_fee      DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_res_slot FOREIGN KEY (slot_id) REFERENCES parking_slots(slot_id),
    CONSTRAINT fk_res_area FOREIGN KEY (area_id) REFERENCES parking_areas(area_id)
);
