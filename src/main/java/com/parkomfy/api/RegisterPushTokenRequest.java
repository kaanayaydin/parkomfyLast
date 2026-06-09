package com.parkomfy.api;

public class RegisterPushTokenRequest {
    private String userId;
    private String expoPushToken;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getExpoPushToken() { return expoPushToken; }
    public void setExpoPushToken(String expoPushToken) { this.expoPushToken = expoPushToken; }
}
