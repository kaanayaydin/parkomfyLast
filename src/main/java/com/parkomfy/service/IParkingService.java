package com.parkomfy.service;

import com.parkomfy.model.*;

import java.util.List;

/**
 * Interface for parking service operations
 * Defines contract for parking management functionality
 */
public interface IParkingService {
    
    /**
     * Find available parking slots
     */
    List<ParkingSlot> findAvailableSlots(ParkingArea area);
    
    /**
     * Occupy a parking slot with a vehicle
     */
    ParkingSession occupySlot(ParkingSlot slot, Vehicle vehicle);
    
    /**
     * Vacate a parking slot
     */
    void vacateSlot(ParkingSlot slot);
    
    /**
     * Get parking session by ID
     */
    ParkingSession getSession(String sessionId);
    
    /**
     * Complete an active/leaving session and create a pending payment (walk-in or manual exit).
     */
    Payment completeSession(ParkingSession session);

    /**
     * Bill a gate visit when no slot session exists (entered but never matched to a slot).
     */
    Payment billGateVisit(Vehicle vehicle, String areaId);
    
    /**
     * Resolve pricing policy for a parking area.
     */
    PricingPolicy getPricingPolicyForArea(String areaId);
    
    /**
     * Calculate parking fee based on duration
     */
    double calculateFee(ParkingSession session, PricingPolicy policy);
    
    /**
     * Get real-time occupancy status
     */
    ParkingArea getRealTimeStatus(ParkingArea area);
}
