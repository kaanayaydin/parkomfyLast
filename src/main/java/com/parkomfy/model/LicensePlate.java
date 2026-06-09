package com.parkomfy.model;

/**
 * LicensePlate class represents a vehicle license plate
 * Encapsulates plate number and validation logic
 */
public class LicensePlate {
    private String plateNumber;
    private String countryCode; // Optional: for international plates
    
    public LicensePlate(String plateNumber) {
        this.plateNumber = plateNumber != null ? plateNumber.toUpperCase().trim() : "";
        this.countryCode = "TR"; // Default to Turkey
    }
    
    public LicensePlate(String plateNumber, String countryCode) {
        this.plateNumber = plateNumber != null ? plateNumber.toUpperCase().trim() : "";
        this.countryCode = countryCode != null ? countryCode.toUpperCase() : "TR";
    }
    
    public String getPlateNumber() {
        return plateNumber;
    }
    
    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber != null ? plateNumber.toUpperCase().trim() : "";
    }
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode != null ? countryCode.toUpperCase() : "TR";
    }
    
    public boolean isValid() {
        // Basic validation: Turkish plate format (e.g., 34ABC123)
        if (plateNumber == null || plateNumber.isEmpty()) {
            return false;
        }
        // Can be extended with more complex validation
        return plateNumber.length() >= 6 && plateNumber.length() <= 10;
    }
    
    @Override
    public String toString() {
        return plateNumber;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LicensePlate that = (LicensePlate) obj;
        return plateNumber.equals(that.plateNumber) && 
               countryCode.equals(that.countryCode);
    }
    
    @Override
    public int hashCode() {
        return plateNumber.hashCode() * 31 + countryCode.hashCode();
    }
}
