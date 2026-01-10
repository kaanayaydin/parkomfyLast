package com.parkomfy.service;

import com.parkomfy.model.Payment;
import com.parkomfy.model.PaymentMethod;
import com.parkomfy.model.ParkingSession;

/**
 * Interface for payment service operations
 * Defines contract for payment processing (Stripe integration)
 */
public interface IPaymentService {
    
    /**
     * Process payment for a parking session
     */
    Payment processPayment(ParkingSession session, PaymentMethod paymentMethod, double amount);
    
    /**
     * Process payment with Stripe API
     */
    Payment processStripePayment(ParkingSession session, PaymentMethod paymentMethod, double amount);
    
    /**
     * Refund a payment
     */
    boolean refundPayment(Payment payment);
    
    /**
     * Get payment status
     */
    Payment.PaymentStatus getPaymentStatus(String paymentId);
}
