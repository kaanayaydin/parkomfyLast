package com.parkomfy.service;

import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ParkingService implements parking management operations
 * Handles slot occupancy, sessions, and fee calculation
 */
public class ParkingService implements IParkingService {
    
    private IParkingRepository repository;
    private IPaymentService paymentService;
    
    public ParkingService(IParkingRepository repository, IPaymentService paymentService) {
        this.repository = repository;
        this.paymentService = paymentService;
    }
    
    @Override
    public List<ParkingSlot> findAvailableSlots(ParkingArea area) {
        return area.getAvailableSlots();
    }
    
    @Override
    public ParkingSession occupySlot(ParkingSlot slot, Vehicle vehicle) {
        if (!slot.isAvailable()) {
            throw new IllegalStateException("Slot " + slot.getSlotId() + " is not available");
        }
        
        slot.occupy(vehicle);
        ParkingSession session = new ParkingSession(vehicle, slot);
        if (vehicle.getEntryTime() != null) {
            session.setEntryTime(vehicle.getEntryTime());
        }
        
        if (repository != null) {
            repository.saveSession(session);
            repository.updateSlot(slot);
        }
        
        return session;
    }
    
    @Override
    public void vacateSlot(ParkingSlot slot) {
        slot.vacate();
        if (repository != null) {
            repository.updateSlot(slot);
        }
    }
    
    @Override
    public ParkingSession getSession(String sessionId) {
        if (repository != null) {
            return repository.getSession(sessionId);
        }
        return null;
    }
    
    @Override
    public Payment completeSession(ParkingSession session) {
        if (session.getPayment() != null) {
            return session.getPayment();
        }
        if (repository != null) {
            Payment existing = repository.getPaymentBySessionId(session.getSessionId());
            if (existing != null) {
                session.setPayment(existing);
                return existing;
            }
        }
        if (!session.isActiveOrLeaving()) {
            throw new IllegalStateException("Session " + session.getSessionId() + " cannot be billed (status=" + session.getStatus() + ")");
        }

        alignSessionEntryWithGate(session);
        session.complete();

        String areaId = session.getParkingSlot() != null
            ? repository.getAreaIdForSlot(session.getParkingSlot().getSlotId())
            : null;
        if (areaId == null && session.getVehicle() != null) {
            areaId = session.getVehicle().getCurrentAreaId();
        }
        PricingPolicy policy = getPricingPolicyForArea(areaId);
        double fee = calculateFee(session, policy);

        if (fee <= 0) {
            if (repository != null) {
                repository.updateSession(session);
                if (session.getVehicle() != null && session.getVehicle().getExitTime() == null) {
                    session.getVehicle().setExitTime(session.getExitTime());
                    repository.saveVehicle(session.getVehicle());
                }
            }
            return null;
        }

        if (repository != null && session.getVehicle() != null) {
            String plate = normalizePlate(session);
            Payment recent = repository.getLatestPaymentForPlate(plate);
            if (isDuplicateRecentPayment(recent)) {
                session.setPayment(recent);
                return recent;
            }
        }

        Payment payment = new Payment(fee, null, session);
        session.setPayment(payment);
        if (repository != null) {
            repository.updateSession(session);
            repository.savePayment(payment);
            if (session.getVehicle() != null && session.getVehicle().getExitTime() == null) {
                session.getVehicle().setExitTime(session.getExitTime());
                repository.saveVehicle(session.getVehicle());
            }
        }
        return payment;
    }

    @Override
    public Payment billGateVisit(Vehicle vehicle, String areaId) {
        if (vehicle == null || vehicle.getEntryTime() == null) {
            throw new IllegalArgumentException("Vehicle entry time is required for billing");
        }
        if (repository != null && vehicle.getLicensePlate() != null) {
            String plate = com.parkomfy.util.PlateMatcher.normalize(
                vehicle.getLicensePlate().getPlateNumber());
            Payment recent = repository.getLatestPaymentForPlate(plate);
            if (isDuplicateRecentPayment(recent)) {
                if (vehicle.getExitTime() == null) {
                    vehicle.setExitTime(LocalDateTime.now());
                    repository.saveVehicle(vehicle);
                }
                return recent;
            }
        }

        LocalDateTime exitTime = LocalDateTime.now();
        vehicle.setExitTime(exitTime);

        ParkingSlot gateSlot = new ParkingSlot("GATE-" + (areaId != null ? areaId : "UNKNOWN"), 0, "G", 0);
        ParkingSession session = new ParkingSession(vehicle, gateSlot);
        session.setEntryTime(vehicle.getEntryTime());
        session.setExitTime(exitTime);
        session.setStatus(ParkingSession.SessionStatus.COMPLETED);

        PricingPolicy policy = getPricingPolicyForArea(areaId);
        double fee = calculateFee(session, policy);

        if (fee <= 0) {
            if (repository != null) {
                repository.saveVehicle(vehicle);
            }
            return null;
        }

        Payment payment = new Payment(fee, null, session);
        session.setPayment(payment);

        if (repository != null) {
            repository.saveSession(session);
            repository.saveVehicle(vehicle);
            repository.savePayment(payment);
        }
        return payment;
    }

    @Override
    public PricingPolicy getPricingPolicyForArea(String areaId) {
        if (areaId != null && repository != null) {
            ParkingArea area = repository.getArea(areaId);
            if (area != null && area.getPricingPolicy() != null) {
                return area.getPricingPolicy();
            }
        }
        return getDefaultPricingPolicy();
    }
    
    @Override
    public double calculateFee(ParkingSession session, PricingPolicy policy) {
        long durationMinutes = session.getDurationMinutes();
        return policy.calculateFee(durationMinutes);
    }
    
    @Override
    public ParkingArea getRealTimeStatus(ParkingArea area) {
        if (repository != null) {
            List<ParkingSlot> slots = repository.getAllSlots(area.getAreaId());
            for (ParkingSlot slot : slots) {
                ParkingSlot areaSlot = area.getSlotById(slot.getSlotId());
                if (areaSlot != null) {
                    areaSlot.setStatus(slot.getStatus());
                    areaSlot.setCurrentVehicle(slot.getCurrentVehicle());
                }
            }
        }
        return area;
    }

    private void alignSessionEntryWithGate(ParkingSession session) {
        Vehicle vehicle = session.getVehicle();
        if (vehicle == null || vehicle.getEntryTime() == null) {
            return;
        }
        if (session.getEntryTime() == null || vehicle.getEntryTime().isBefore(session.getEntryTime())) {
            session.setEntryTime(vehicle.getEntryTime());
        }
    }
    
    private PricingPolicy getDefaultPricingPolicy() {
        return new PricingPolicy("DEFAULT", 20.0);
    }

    private boolean isDuplicateRecentPayment(Payment recent) {
        if (recent == null || recent.getPaymentTime() == null) {
            return false;
        }
        long seconds = java.time.Duration.between(recent.getPaymentTime(), LocalDateTime.now()).getSeconds();
        return seconds >= 0 && seconds < 180;
    }

    private String normalizePlate(ParkingSession session) {
        if (session.getVehicle() == null || session.getVehicle().getLicensePlate() == null) {
            return "";
        }
        return com.parkomfy.util.PlateMatcher.normalize(
            session.getVehicle().getLicensePlate().getPlateNumber());
    }
}
