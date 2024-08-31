package com.test.test;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.test.test.maze.MazeWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new CarWebSocketHandler(), "/car").setAllowedOrigins("*");
        registry.addHandler(new MazeWebSocketHandler(), "/mazeLevels").setAllowedOrigins("*");
    }
}

