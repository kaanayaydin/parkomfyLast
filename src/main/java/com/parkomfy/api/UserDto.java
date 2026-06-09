package com.parkomfy.api;

import com.parkomfy.model.User;

public class UserDto {
    private String userId;
    private String email;
    private String fullName;
    private String licensePlate;
    private String role;

    public UserDto() {}

    public UserDto(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.licensePlate = user.getLicensePlate();
        this.role = user.getRole();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
