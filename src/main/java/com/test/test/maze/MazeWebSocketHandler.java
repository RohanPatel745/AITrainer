package com.test.test.maze;

import java.io.IOException;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MazeWebSocketHandler extends TextWebSocketHandler{
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static WebSocketSession webSocketSession;
    private static boolean isRunning = false;
    private static int level;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        webSocketSession = session;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        if (jsonNode.has("action")) {
            String action = jsonNode.get("action").asText();
            switch (action) {
                case "runLevelOne":
                    if (!isRunning) {
                        isRunning = true;
                        level = 1;
                        new Thread(() -> {
                            QLearningEnvironment.setShouldInterrupt(false);
                            QLearningEnvironment.train(1, jsonNode.get("blankSpaceReward").asDouble(),
                                jsonNode.get("trophySpaceReward").asDouble());
                        }).start();
                    }
                    break;
                case "runLevelTwo":
                    if (!isRunning) {
                        isRunning = true;
                        level = 2;
                        new Thread(() -> {
                            QLearningEnvironment.setShouldInterrupt(false);
                            QLearningEnvironment.train(2, jsonNode.get("blankSpaceReward").asDouble(),
                                jsonNode.get("trophySpaceReward").asDouble(), jsonNode.get("xSpaceReward").asDouble());
                        }).start();
                    }
                    break;
                case "runLevelThree":
                    if (!isRunning) {
                        isRunning = true;
                        level = 3;
                        new Thread(() -> {
                            QLearningEnvironment.setShouldInterrupt(false);
                            QLearningEnvironment.train(3, jsonNode.get("blankSpaceReward").asDouble(),
                                jsonNode.get("trophySpaceReward").asDouble(), jsonNode.get("xSpaceReward").asDouble());
                        }).start();
                    }
                    break;
                case "runLevelFour":
                    if (!isRunning) {
                        isRunning = true;
                        level = 4;
                        new Thread(() -> {
                            QLearningEnvironment.setShouldInterrupt(false);
                            QLearningEnvironment.train(4, jsonNode.get("blankSpaceReward").asDouble(),
                                jsonNode.get("trophySpaceReward").asDouble(), jsonNode.get("enemySpaceReward").asDouble());
                        }).start();
                    }
                    break;
                case "runLevelFive":
                    if (!isRunning) {
                        isRunning = true;
                        level = 5;
                        new Thread(() -> {
                            QLearningEnvironment.setShouldInterrupt(false);
                            QLearningEnvironment.train(5, jsonNode.get("blankSpaceReward").asDouble(),
                                jsonNode.get("trophySpaceReward").asDouble(), jsonNode.get("xSpaceReward").asDouble(),
                                jsonNode.get("enemySpaceReward").asDouble());
                        }).start();
                    }
                    break;
                case "stop":
                        new Thread(() -> {
                            QLearningEnvironment.setShouldInterrupt(true);
                            QLearningEnvironment.reset();
                            isRunning = false;
                        }).start();
                    break;
            }
        }
    }

    public static synchronized void sendMazeState(int[] state) throws IOException{
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.put("x", state[0]);
        jsonNode.put("y", state[1]);
        jsonNode.put("epoch", QLearningEnvironment.getEpoch());
        jsonNode.put("shouldReset", QLearningEnvironment.shouldReset());

        String response = objectMapper.writeValueAsString(jsonNode);
        webSocketSession.sendMessage(new TextMessage(response));

        if (QLearningEnvironment.getEpoch() > 2000) {
            QLearningEnvironment.setShouldInterrupt(true);
            isRunning = false;
        }
    }
}
