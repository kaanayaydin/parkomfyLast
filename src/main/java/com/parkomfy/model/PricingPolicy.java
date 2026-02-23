package com.parkomfy.model;

/**
 * PricingPolicy class defines parking pricing rules
 * Supports different pricing models (hourly, daily, etc.)
 */
public class PricingPolicy {
    private String policyId;
    private double hourlyRate;
    private double dailyRate;
    private double firstHourRate; // Often different from hourly rate
    private int freeMinutes; // Free parking duration in minutes
    private double maxDailyRate; // Maximum charge per day
    private Currency currency;
    
    public PricingPolicy(String policyId, double hourlyRate) {
        this.policyId = policyId;
        this.hourlyRate = hourlyRate;
        this.firstHourRate = hourlyRate;
        this.dailyRate = hourlyRate * 24;
        this.freeMinutes = 0;
        this.maxDailyRate = dailyRate;
        this.currency = Currency.TRY; // Turkish Lira
    }
    
    public String getPolicyId() {
        return policyId;
    }
    
    public double getHourlyRate() {
        return hourlyRate;
    }
    
    public void setHourlyRate(double hourlyRate) {
        this.hourlyRate = hourlyRate;
    }
    
    public double getDailyRate() {
        return dailyRate;
    }
    
    public void setDailyRate(double dailyRate) {
        this.dailyRate = dailyRate;
    }
    
    public double getFirstHourRate() {
        return firstHourRate;
    }
    
    public void setFirstHourRate(double firstHourRate) {
        this.firstHourRate = firstHourRate;
    }
    
    public int getFreeMinutes() {
        return freeMinutes;
    }
    
    public void setFreeMinutes(int freeMinutes) {
        this.freeMinutes = freeMinutes;
    }
    
    public double getMaxDailyRate() {
        return maxDailyRate;
    }
    
    public void setMaxDailyRate(double maxDailyRate) {
        this.maxDailyRate = maxDailyRate;
    }
    
    public Currency getCurrency() {
        return currency;
    }
    
    public void setCurrency(Currency currency) {
        this.currency = currency;
    }
    
    /**
     * Calculate parking fee based on duration in minutes
     */
    public double calculateFee(long durationMinutes) {
        if (durationMinutes <= freeMinutes) {
            return 0.0;
        }
        
        // Subtract free minutes
        long billableMinutes = durationMinutes - freeMinutes;
        
        // Convert to hours (round up)
        double hours = Math.ceil(billableMinutes / 60.0);
        
        double fee = 0.0;
        
        if (hours <= 1.0) {
            // First hour
            fee = firstHourRate;
        } else {
            // First hour + additional hours
            fee = firstHourRate + (hours - 1.0) * hourlyRate;
        }
        
        // Apply daily maximum
        if (fee > maxDailyRate) {
            fee = maxDailyRate;
        }
        
        return Math.round(fee * 100.0) / 100.0; // Round to 2 decimal places
    }
    
    @Override
    public String toString() {
        return "PricingPolicy{" +
                "policyId='" + policyId + '\'' +
                ", hourlyRate=" + hourlyRate +
                ", currency=" + currency +
                '}';
    }
    
    /**
     * Enum for currency
     */
    public enum Currency {
        TRY,  // Turkish Lira
        USD,  // US Dollar
        EUR   // Euro
    }
}
