package com.parkomfy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * User class represents a user of the PARKOMFY mobile application
 * Contains user information, payment methods, and parking history
 */
public class User {
    private String userId;
    private String email;
    private String phoneNumber;
    private String fullName;
    private String passwordHash;
    private String licensePlate;
    private String role;
    private List<LicensePlate> registeredVehicles;
    private PaymentMethod defaultPaymentMethod;
    private List<ParkingSession> parkingHistory;
    
    public User(String userId, String email, String phoneNumber, String fullName) {
        this.userId = userId;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.fullName = fullName;
        this.role = "USER";
        this.registeredVehicles = new ArrayList<>();
        this.parkingHistory = new ArrayList<>();
    }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getUserId() {
        return userId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public List<LicensePlate> getRegisteredVehicles() {
        return new ArrayList<>(registeredVehicles);
    }
    
    public void addVehicle(LicensePlate licensePlate) {
        if (!registeredVehicles.contains(licensePlate)) {
            registeredVehicles.add(licensePlate);
        }
    }
    
    public void removeVehicle(LicensePlate licensePlate) {
        registeredVehicles.remove(licensePlate);
    }
    
    public boolean hasVehicle(LicensePlate licensePlate) {
        return registeredVehicles.contains(licensePlate);
    }
    
    public PaymentMethod getDefaultPaymentMethod() {
        return defaultPaymentMethod;
    }
    
    public void setDefaultPaymentMethod(PaymentMethod defaultPaymentMethod) {
        this.defaultPaymentMethod = defaultPaymentMethod;
    }
    
    public List<ParkingSession> getParkingHistory() {
        return new ArrayList<>(parkingHistory);
    }
    
    public void addParkingSession(ParkingSession session) {
        parkingHistory.add(session);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", registeredVehicles=" + registeredVehicles.size() +
                '}';
    }
}
