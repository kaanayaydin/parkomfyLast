package com.parkomfy.api;

import com.parkomfy.model.*;
import java.time.LocalDateTime;

/**
 * Generic API Response wrapper for all REST endpoints
 */
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private int statusCode;
    private LocalDateTime timestamp;
    
    private ApiResponse(boolean success, T data, String message, int statusCode) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now();
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, 200);
    }
    
    public static <T> ApiResponse<T> success(T data, String message, int statusCode) {
        return new ApiResponse<>(true, data, message, statusCode);
    }
    
    public static <T> ApiResponse<T> error(String message, int statusCode) {
        return new ApiResponse<>(false, null, message, statusCode);
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getMessage() { return message; }
    public int getStatusCode() { return statusCode; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
