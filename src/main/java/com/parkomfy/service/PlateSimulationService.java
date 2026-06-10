package com.parkomfy.service;

import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;
import com.parkomfy.util.PlateMatcher;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Giriş kamerası simülasyonu: sabit plakalar otopark içinde var sayılır.
 */
public class PlateSimulationService {

    public static final List<String> SIMULATED_PLATES = Arrays.asList(
        "34 PGZ 545",
        "67 ACP 810"
    );

    private final IParkingRepository repository;

    public PlateSimulationService(IParkingRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void seedSimulatedVehicles() {
        LocalDateTime now = LocalDateTime.now();
        for (String plate : SIMULATED_PLATES) {
            String normalized = PlateMatcher.normalize(plate);
            Vehicle vehicle = repository.getVehicleByPlate(new LicensePlate(normalized));
            if (vehicle == null) {
                vehicle = new Vehicle(new LicensePlate(normalized));
            }
            vehicle.setEntryTime(now);
            vehicle.setExitTime(null);
            repository.saveVehicle(vehicle);
        }
    }

    /** Dolu slota oturum yoksa simüle plakalardan birini ata. */
    public void assignPlateToOccupiedSlot(ParkingSlot slot, String areaId) {
        if (slot.getStatus() != ParkingSlot.SlotStatus.OCCUPIED) {
            return;
        }
        if (repository.getActiveSessionForSlot(slot.getSlotId()) != null) {
            return;
        }

        Set<String> usedPlates = new HashSet<>();
        for (ParkingSession s : repository.getActiveSessions()) {
            if (s.getVehicle() != null && s.getVehicle().getLicensePlate() != null) {
                usedPlates.add(PlateMatcher.normalize(s.getVehicle().getLicensePlate().getPlateNumber()));
            }
        }

        for (String plate : SIMULATED_PLATES) {
            String normalized = PlateMatcher.normalize(plate);
            if (usedPlates.contains(normalized)) {
                continue;
            }
            Vehicle vehicle = repository.getVehicleByPlate(new LicensePlate(normalized));
            if (vehicle == null) {
                vehicle = new Vehicle(new LicensePlate(normalized));
                vehicle.setEntryTime(LocalDateTime.now());
                repository.saveVehicle(vehicle);
            }
            if (slot.isAvailable()) {
                slot.occupy(vehicle);
            } else {
                slot.setCurrentVehicle(vehicle);
            }
            ParkingSession session = new ParkingSession(vehicle, slot);
            repository.saveSession(session);
            repository.updateSlot(slot);
            return;
        }
    }

    public Vehicle getOrCreateVehicle(String plate) {
        String normalized = PlateMatcher.normalize(plate);
        Vehicle vehicle = repository.getVehicleByPlate(new LicensePlate(normalized));
        if (vehicle == null) {
            vehicle = new Vehicle(new LicensePlate(normalized));
            vehicle.setEntryTime(LocalDateTime.now());
        }
        User owner = repository.getUserByLicensePlate(normalized);
        if (owner != null) {
            vehicle.setUserId(owner.getUserId());
        }
        repository.saveVehicle(vehicle);
        return vehicle;
    }
}
