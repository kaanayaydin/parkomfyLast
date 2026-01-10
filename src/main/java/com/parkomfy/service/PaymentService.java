package com.parkomfy.service;

import com.parkomfy.model.Payment;
import com.parkomfy.model.PaymentMethod;
import com.parkomfy.model.ParkingSession;
import com.parkomfy.repository.IParkingRepository;

/**
 * PaymentService implements payment processing operations
 * Integrates with Stripe API for payment processing
 */
public class PaymentService implements IPaymentService {
    
    private IParkingRepository repository;
    private StripeIntegration stripeIntegration;
    
    public PaymentService(IParkingRepository repository) {
        this.repository = repository;
        this.stripeIntegration = new StripeIntegration();
    }
    
    @Override
    public Payment processPayment(ParkingSession session, PaymentMethod paymentMethod, double amount) {
        Payment payment = session.getPayment();
        if (payment == null) {
            payment = new Payment(amount, paymentMethod, session);
        }
        
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(amount);
        
        // Process via Stripe
        return processStripePayment(session, paymentMethod, amount);
    }
    
    @Override
    public Payment processStripePayment(ParkingSession session, PaymentMethod paymentMethod, double amount) {
        Payment payment = session.getPayment();
        if (payment == null) {
            payment = new Payment(amount, paymentMethod, session);
        }
        
        try {
            // Simulate Stripe API call
            String transactionId = stripeIntegration.chargePayment(
                paymentMethod.getStripePaymentMethodId(), 
                amount
            );
            
            if (transactionId != null) {
                payment.markAsCompleted(transactionId);
                
                // Update session
                session.setPayment(payment);
                
                // Save to repository
                if (repository != null) {
                    repository.savePayment(payment);
                    repository.updateSession(session);
                }
            } else {
                payment.markAsFailed();
            }
        } catch (Exception e) {
            payment.markAsFailed();
            System.err.println("Payment processing failed: " + e.getMessage());
        }
        
        return payment;
    }
    
    @Override
    public boolean refundPayment(Payment payment) {
        if (!payment.isCompleted()) {
            return false;
        }
        
        try {
            boolean success = stripeIntegration.refundPayment(payment.getTransactionId());
            if (success) {
                payment.setStatus(Payment.PaymentStatus.REFUNDED);
                if (repository != null) {
                    repository.updatePayment(payment);
                }
            }
            return success;
        } catch (Exception e) {
            System.err.println("Refund failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public Payment.PaymentStatus getPaymentStatus(String paymentId) {
        if (repository != null) {
            Payment payment = repository.getPayment(paymentId);
            return payment != null ? payment.getStatus() : null;
        }
        return null;
    }
    
    /**
     * StripeIntegration class simulates Stripe API calls
     * In real implementation, this would use Stripe Java SDK
     */
    private static class StripeIntegration {
        
        public String chargePayment(String paymentMethodId, double amount) {
            // Simulate Stripe API call
            // In real implementation: Stripe.apiKey = "sk_test_...";
            // Charge charge = Charge.create(params);
            
            if (paymentMethodId != null && amount > 0) {
                // Simulate successful transaction
                return "txn_" + System.currentTimeMillis();
            }
            return null;
        }
        
        public boolean refundPayment(String transactionId) {
            // Simulate Stripe refund API call
            return transactionId != null && transactionId.startsWith("txn_");
        }
    }
}
