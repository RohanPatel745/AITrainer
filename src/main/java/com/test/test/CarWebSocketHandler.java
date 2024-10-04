package com.test.test;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.nd4j.nativeblas.Nd4jCpu.split;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;


public class CarWebSocketHandler extends TextWebSocketHandler {
    private double carX = 7;
    private double carY = 47;
    private double carAngle = 90;
    private double carVelocity = 0;

    private double startingCarX;
    private double startingCarY;
    private double startingCarAngle;

    private int epoch = 0;
    private boolean gameFinished = false;
    private boolean gameWon = false;
    private double[] currentState = new double[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private double[] oldState;

    private boolean running = false;
    private boolean testing = false;
    private double closestDistance;
    private double maxDistance = 0;
    private double[] times = new double[1005];

    private double moveCloserReward;
    private double moveFartherReward;
    private double collisionReward;
    private double goalReward;
    private double speedReward;

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

                if ((gameFinished || gameWon) && (!running && !testing)) reset(true);
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
                    gameFinished = true;
                    break;
                case "won":
                    gameWon = true;
                    break;
                case "run":
                    testing = false;

                    double[] rewards = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                    moveCloserReward = rewards[0];
                    moveFartherReward = rewards[1];
                    collisionReward = rewards[2];
                    goalReward = rewards[3];
                    speedReward = rewards[4];

                    startingCarX = carX;
                    startingCarY = carY;
                    startingCarAngle = carAngle;

                    runDQN();
                    break;
                case "distances":
                    currentState = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                    currentState[12] = carVelocity;
                    break;
                case "test":
                    testing = !testing;
                    reset(false);
                    break;
                case "updateCarPosition":
                    double[] carPosition = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                    carX = carPosition[0];
                    carY = carPosition[1];
                    break;
                case "updateCarAngle":
                    double[] carRotation = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                    carAngle = carRotation[0];
                    break;
            }
        }
    }

    private synchronized void sendCarState() throws IOException{
        String response = String.format("{\"x\": %f, \"y\": %f, \"rotation\": %f, \"epoch\": %d}", carX, carY, carAngle, epoch);
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

    private void reset(boolean resetTimes) {
        carX = startingCarX;
        carY = startingCarY;
        carVelocity = 0;
        carAngle = startingCarAngle;
        closestDistance = maxDistance;
        gameFinished = false;
        gameWon = false;
        if (resetTimes) for (int i = 0; i < 1005; i++) times[i] = 0;
    }

    private synchronized void runDQN() {
        running = true;
        int inputSize = 13;
        int actionSize = 4;
        int hiddenSize = 5;
        int replayCapacity = 1000;

        maxDistance = currentState[11];
        closestDistance = maxDistance;
        for (int i = 0; i < 1000; i++) times[i] = 0;

        DQNAgent agent = new DQNAgent(inputSize, actionSize, hiddenSize, replayCapacity);
        Thread thread = new Thread(() -> {
            while (true) {
                double totalReward = 0;
                int moveNumber = 0;
                boolean shouldReset = false;
                if (!testing) {
                    while (!testing) {
                        double startTime = System.currentTimeMillis();
                        int action = agent.chooseAction(currentState);
                        oldState = Arrays.copyOf(currentState, currentState.length);
                        switch (action) {
                            case 0 -> {
                                for (int i = 0; i < 3; i++) accelerate();
                            }
                            case 1 -> {
                                for (int i = 0; i < 3; i++) brake();
                            }
                            case 2 -> turnLeft();
                            case 3 -> turnRight();
                        }

                        try {
                            sendCarState();
                            Thread.sleep(16);
                        } catch (Exception ignored) {
                        }

                        double reward;

                        if (gameFinished) {
                            reward = collisionReward;
                            shouldReset = true;
                        }
                        else if (gameWon) {
                            reward = goalReward;
                            shouldReset = true;
                            testing = true;
                        }
                        else if (currentState[11] < closestDistance) {
                            reward = moveCloserReward * (maxDistance - closestDistance) / 10d;
                            closestDistance = currentState[11];
                        }
                        else reward = moveFartherReward / 100d;

                        if (carVelocity > 0) {
                            reward += carVelocity * 5 * speedReward;
                        }

                        // System.out.println("Closest Distance: " + closestDistance + " Max Distance: " + maxDistance);
                        // System.out.println("Current Distance: " + currentState[11]);
                        // System.out.println("reward: " + reward);

                        totalReward += reward;

                        agent.storeExperience(oldState, action, reward, currentState, gameFinished || gameWon);
                        agent.train();

                        moveNumber++;
                        if (moveNumber >= 500 || shouldReset) break;

                        times[moveNumber] = System.currentTimeMillis() - startTime;
                    }
                    epoch++;
                    System.out.println("Total Reward: " + totalReward);
                    if (!testing) reset(true);
                    else reset(false);
                    moveNumber = 0;
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    while (testing) {
                        int action = agent.chooseBestAction(currentState);
                        oldState = Arrays.copyOf(currentState, currentState.length);
                        switch (action) {
                            case 0 -> {
                                for (int i = 0; i < 3; i++) accelerate();
                            }
                            case 1 -> {
                                for (int i = 0; i < 3; i++) brake();
                            }
                            case 2 -> turnLeft();
                            case 3 -> turnRight();
                        }

                        try {
                            sendCarState();
                            if (times[moveNumber] != 0) Thread.sleep((long) (times[moveNumber] - 1));
                            else Thread.sleep(52);
                        } catch (Exception e) {
                            System.out.println(times[moveNumber]);
                        }

                        moveNumber++;
                        if (gameFinished) testing = false;
                        if (gameFinished || gameWon || moveNumber > 1000) break;
                    }
                    reset(false);
                    moveNumber = 0;
                }   
            }
        });
        thread.start();
    }
}

