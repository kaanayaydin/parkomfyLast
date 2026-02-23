package com.parkomfy.api;

import com.parkomfy.model.Payment;
import java.time.LocalDateTime;

/**
 * DTO for Payment
 */
public class PaymentDto {
    private String paymentId;
    private String sessionId;
    private double amount;
    private String currency;
    private String status;
    private LocalDateTime timestamp;
    
    public PaymentDto() {}
    
    public PaymentDto(Payment payment) {
        this.paymentId = payment.getPaymentId();
        this.sessionId = payment.getParkingSession().getSessionId();
        this.amount = payment.getAmount();
        this.currency = "TRY";
        this.status = payment.getStatus().toString();
        this.timestamp = payment.getPaymentTime();
    }
    
    // Getters and Setters
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
