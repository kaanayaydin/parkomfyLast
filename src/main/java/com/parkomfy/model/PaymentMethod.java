package com.parkomfy.model;

/**
 * PaymentMethod class represents a payment method (credit card, etc.)
 * Used for Stripe API integration
 */
public class PaymentMethod {
    private String methodId;
    private PaymentType type;
    private String lastFourDigits; // For cards
    private String cardBrand; // e.g., "Visa", "Mastercard"
    private boolean isDefault;
    private String stripePaymentMethodId; // Stripe API ID
    
    public PaymentMethod(PaymentType type) {
        this.methodId = generateMethodId();
        this.type = type;
        this.isDefault = false;
    }
    
    private String generateMethodId() {
        return "PM-" + System.currentTimeMillis();
    }
    
    public String getMethodId() {
        return methodId;
    }
    
    public PaymentType getType() {
        return type;
    }
    
    public void setType(PaymentType type) {
        this.type = type;
    }
    
    public String getLastFourDigits() {
        return lastFourDigits;
    }
    
    public void setLastFourDigits(String lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
    }
    
    public String getCardBrand() {
        return cardBrand;
    }
    
    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }
    
    public boolean isDefault() {
        return isDefault;
    }
    
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
    
    public String getStripePaymentMethodId() {
        return stripePaymentMethodId;
    }
    
    public void setStripePaymentMethodId(String stripePaymentMethodId) {
        this.stripePaymentMethodId = stripePaymentMethodId;
    }
    
    @Override
    public String toString() {
        return "PaymentMethod{" +
                "type=" + type +
                ", lastFourDigits='" + lastFourDigits + '\'' +
                ", cardBrand='" + cardBrand + '\'' +
                '}';
    }
    
    /**
     * Enum for payment types
     */
    public enum PaymentType {
        CREDIT_CARD,
        DEBIT_CARD,
        DIGITAL_WALLET
    }
}
