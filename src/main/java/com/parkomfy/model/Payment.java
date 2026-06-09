package com.parkomfy.model;

import java.time.LocalDateTime;

/**
 * Payment class represents a payment transaction
 * Contains payment details, amount, method, and status
 */
public class Payment {
    private String paymentId;
    private double amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private LocalDateTime paymentTime;
    private String transactionId; // From Stripe or other payment gateway
    private ParkingSession parkingSession;
    
    public Payment(double amount, PaymentMethod paymentMethod, ParkingSession parkingSession) {
        this.paymentId = generatePaymentId();
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.parkingSession = parkingSession;
        this.status = PaymentStatus.PENDING;
        this.paymentTime = LocalDateTime.now();
    }
    
    private String generatePaymentId() {
        return "PAY-" + System.currentTimeMillis() + "-" + 
               (int)(Math.random() * 10000);
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public PaymentStatus getStatus() {
        return status;
    }
    
    public void setStatus(PaymentStatus status) {
        this.status = status;
        if (status == PaymentStatus.COMPLETED) {
            this.paymentTime = LocalDateTime.now();
        }
    }
    
    public LocalDateTime getPaymentTime() {
        return paymentTime;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public ParkingSession getParkingSession() {
        return parkingSession;
    }
    
    public void setParkingSession(ParkingSession parkingSession) {
        this.parkingSession = parkingSession;
    }
    
    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }
    
    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }
    
    public void markAsCompleted(String transactionId) {
        this.status = PaymentStatus.COMPLETED;
        this.transactionId = transactionId;
        this.paymentTime = LocalDateTime.now();
    }
    
    public void markAsFailed() {
        this.status = PaymentStatus.FAILED;
    }
    
    @Override
    public String toString() {
        return "Payment{" +
                "paymentId='" + paymentId + '\'' +
                ", amount=" + amount +
                ", status=" + status +
                ", paymentTime=" + paymentTime +
                '}';
    }
    
    /**
     * Enum for payment status
     */
    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED
    }
}
