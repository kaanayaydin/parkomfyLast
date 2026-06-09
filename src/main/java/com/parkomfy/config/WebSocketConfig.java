package com.parkomfy.config;

import com.parkomfy.websocket.ParkingWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ParkingWebSocketHandler parkingWebSocketHandler;

    public WebSocketConfig(ParkingWebSocketHandler parkingWebSocketHandler) {
        this.parkingWebSocketHandler = parkingWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(parkingWebSocketHandler, "/ws/parking")
            .setAllowedOrigins("*");
    }
}
