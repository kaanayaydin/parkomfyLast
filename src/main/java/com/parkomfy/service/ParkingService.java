package com.parkomfy.service;

import com.parkomfy.model.*;
import com.parkomfy.repository.IParkingRepository;

import java.util.List;
import java.util.stream.Collectors;

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
        
        // Save to repository
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
        if (!session.isActive()) {
            throw new IllegalStateException("Session " + session.getSessionId() + " is not active");
        }
        
        // Complete the session
        session.complete();
        
        // Calculate fee
        ParkingArea area = findAreaForSlot(session.getParkingSlot());
        PricingPolicy policy = area != null ? area.getPricingPolicy() : getDefaultPricingPolicy();
        double fee = calculateFee(session, policy);
        
        // Create payment
        Payment payment = new Payment(fee, null, session);
        session.setPayment(payment);
        
        // Save to repository
        if (repository != null) {
            repository.updateSession(session);
            repository.savePayment(payment);
        }
        
        return payment;
    }
    
    @Override
    public double calculateFee(ParkingSession session, PricingPolicy policy) {
        long durationMinutes = session.getDurationMinutes();
        return policy.calculateFee(durationMinutes);
    }
    
    @Override
    public ParkingArea getRealTimeStatus(ParkingArea area) {
        // Refresh slot statuses from repository if needed
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
    
    private ParkingArea findAreaForSlot(ParkingSlot slot) {
        // This would typically query the repository
        // For now, return null (would need area reference)
        return null;
    }
    
    private PricingPolicy getDefaultPricingPolicy() {
        return new PricingPolicy("DEFAULT", 10.0); // 10 TRY per hour
    }
}
