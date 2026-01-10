package com.parkomfy.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Simple HTTP client to test YOLO server without gRPC
 * Useful for VSCode stability - no heavy networking libraries
 */
public class YOLOServerTest {
    
    private static final Logger LOGGER = Logger.getLogger(YOLOServerTest.class.getName());
    private static final String YOLO_SERVER_URL = "http://127.0.0.1:50051";
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(5))
        .build();
    
    public static void main(String[] args) {
        LOGGER.info("=" .repeat(60));
        LOGGER.info("YOLO Server HTTP Client Test");
        LOGGER.info("=" .repeat(60));
        
        try {
            // Test 1: Health check
            LOGGER.info("\n✓ Test 1: Health Check");
            String healthResponse = sendGetRequest("/health");
            LOGGER.info("Response: " + healthResponse);
            
            // Test 2: Status check
            LOGGER.info("\n✓ Test 2: Status Check");
            String statusResponse = sendGetRequest("/status");
            LOGGER.info("Response: " + statusResponse);
            
            // Test 3: Detection
            LOGGER.info("\n✓ Test 3: Vehicle Detection");
            String detectionResponse = sendGetRequest("/detect");
            LOGGER.info("Response: " + detectionResponse);
            
            // Test 4: POST Detection with camera ID
            LOGGER.info("\n✓ Test 4: POST Detection Request");
            String postResponse = sendPostRequest("/detect", "{\"camera_id\": \"camera_001\"}");
            LOGGER.info("Response: " + postResponse);
            
            LOGGER.info("\n" + "=" .repeat(60));
            LOGGER.info("✅ All tests passed successfully!");
            LOGGER.info("=" .repeat(60));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Test failed: " + e.getMessage(), e);
            System.exit(1);
        }
    }
    
    private static String sendGetRequest(String endpoint) throws IOException, InterruptedException {
        String url = YOLO_SERVER_URL + endpoint;
        LOGGER.info("GET " + url);
        
        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        
        return response.body();
    }
    
    private static String sendPostRequest(String endpoint, String jsonBody) 
            throws IOException, InterruptedException {
        String url = YOLO_SERVER_URL + endpoint;
        LOGGER.info("POST " + url);
        
        HttpRequest request = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        
        return response.body();
    }
}
