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
     * Complete a parking session and calculate fee
     */
    Payment completeSession(ParkingSession session);
    
    /**
     * Calculate parking fee based on duration
     */
    double calculateFee(ParkingSession session, PricingPolicy policy);
    
    /**
     * Get real-time occupancy status
     */
    ParkingArea getRealTimeStatus(ParkingArea area);
}
