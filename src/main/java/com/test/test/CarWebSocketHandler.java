package com.test.test;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.util.Arrays;


public class CarWebSocketHandler extends TextWebSocketHandler {

    private double carX = 7;
    private double carY = 47;
    private double carAngle = 90;
    private double carVelocity = 0;
    private int episode = 0;
    private boolean gameFinished = false;
    private boolean gameWon = false;
    private double[] currentState = new double[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private double[] oldState;
    private boolean running = false;
    private boolean testing = false;
    private double closestDistance = 255;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSession webSocketSession;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        webSocketSession = session;
        new Thread(() -> {
            while (true) {
                carX += (carVelocity * Math.sin(Math.toRadians(carAngle)));
                carY -= (carVelocity * Math.cos(Math.toRadians(carAngle)));

                carVelocity = Math.round(carVelocity * 1000) / 1000d;

                if (carVelocity > 0) carVelocity -= .01;
                else if (carVelocity < 0) carVelocity += .01;

                try {
                    sendCarState();
                    Thread.sleep(16);
                } catch (Exception ignored) {
                }
            }
            
        }).start();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        if (jsonNode.has("action")) {
            String action = jsonNode.get("action").asText();
            switch (action) {
                case "accelerate":
                    accelerate();
                    break;
                case "brake":
                    brake();
                    break;
                case "turnLeft":
                    turnLeft();
                    break;
                case "turnRight":
                    turnRight();
                    break;
                case "collision":
                    reset();
                    gameFinished = true;
                    break;
                case "run":
                    testing = false;
                    runDQN();
                    break;
                case "distances":
                    currentState = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                    break;
                case "test":
                    testing = !testing;
                    reset();
                    break;
            }
        }
    }

    private synchronized void sendCarState() throws IOException{
        String response = String.format("{\"x\": %f, \"y\": %f, \"rotation\": %f, \"episode\": %d}", carX, carY, carAngle, episode);
        webSocketSession.sendMessage(new TextMessage(response));
    }

    private void accelerate() {
        if (carVelocity < 1.5) carVelocity += .02;
    }

    private void brake() {
        if (carVelocity > -1) carVelocity -= .02;
    }

    private void turnLeft() {
        carAngle -= 1;
    }

    private void turnRight() {
        carAngle += 1;
    }

    private void reset() {
        carX = 7;
        carY = 47;
        carAngle = 90;
        closestDistance = 255;
    }

    private synchronized void runDQN() {
        running = true;
        int stateSize = 12;
        int actionSize = 4;
        int hiddenSize = 8;
        int replayCapacity = 1000;

        DQNAgent agent = new DQNAgent(stateSize, actionSize, hiddenSize, replayCapacity);

        Thread thread = new Thread(() -> {
            while (true) {
                if (!testing) {
                    while (!(gameFinished || gameWon) && !testing) {
                        int action = agent.chooseAction(currentState);
                        oldState = Arrays.copyOf(currentState, 12);
                        switch (action) {
                            case 0 -> accelerate();
                            case 1 -> brake();
                            case 2 -> turnLeft();
                            case 3 -> turnRight();
                        }

                        try {
                            sendCarState();
                            Thread.sleep(16);
                        } catch (Exception ignored) {
                        }

                        double reward = -5;
                        if (currentState[11] < closestDistance) {
                            reward = 10;
                            closestDistance = currentState[11];
                        }

                        if (gameFinished) reward = -250;
                        else if (gameWon) reward = 1000;

                        agent.storeExperience(oldState, action, reward, currentState, gameFinished || gameWon);
                        agent.train();
                    }

                    episode++;
                    gameFinished = false;
                    gameWon = false;
                } else {
                    while (testing) {
                        int action = agent.chooseBestAction(currentState);
                        oldState = Arrays.copyOf(currentState, 12);
                        switch (action) {
                            case 0 -> accelerate();
                            case 1 -> brake();
                            case 2 -> turnLeft();
                            case 3 -> turnRight();
                        }

                        try {
                            sendCarState();
                            Thread.sleep(16);
                        } catch (Exception ignored) {
                        }
                    }
                }   
            }
        });
        thread.start();
    }
}

