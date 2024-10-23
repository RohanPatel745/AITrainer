package com.test.test;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.nativeblas.Nd4jCpu.split;
import org.springframework.web.socket.TextMessage;

import java.io.File;
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
    private double[] currentState = new double[14];
    private double[] oldState;
    private DQNAgent agent;

    private boolean running = false;
    private boolean testing = false;
    private boolean optimizing = false;
    private double closestDistance;
    private double maxDistance = 0;
    private double[] times = new double[1005];

    private double moveCloserReward;
    private double moveFartherReward;
    private double collisionReward;
    private double goalReward;

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

                    startingCarX = carX;
                    startingCarY = carY;
                    startingCarAngle = carAngle;

                    runDQN();
                    break;
                case "distances":
                    currentState = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                    currentState[12] = carVelocity;
                    currentState[13] = closestDistance;
                    break;
                case "test":
                    testing = !testing;
                    reset(false);
                    break;
                case "save":
                    saveModel();
                    break;
                case "load":
                    loadModel();
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
        int inputSize = 14;
        int actionSize = 4;
        int hiddenSize = 5;
        int replayCapacity = 5000;

        maxDistance = currentState[11];
        closestDistance = maxDistance;
        for (int i = 0; i < 1005; i++) times[i] = 0;


        agent = new DQNAgent(inputSize, actionSize, hiddenSize, replayCapacity);
        Thread thread = new Thread(() -> {
            int totalTimesFinished = 0;
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
                                for (int i = 0; i < 5; i++) accelerate();
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

                        double reward = 0;

                        if (gameFinished || moveNumber >= 999) {
                            reward = collisionReward;
                            // if (epoch > 300) reward *= 1.5;
                            shouldReset = true;
                        }
                        else if (gameWon) {
                            reward = goalReward;
                            shouldReset = true;
                            testing = true;
                        }
                        else if (currentState[11] < closestDistance) {
                            reward = (moveCloserReward * (Math.abs(carVelocity) + .75) * (maxDistance - currentState[11])) / 10d;
                            if (optimizing) reward = moveCloserReward * Math.pow((Math.abs(carVelocity) + 1), 2) * (maxDistance - currentState[11]) / 25d;
                            if (reward < 0) reward = 0;
                            closestDistance = currentState[11];
                        } else {
                            reward = moveFartherReward / 10d;
                            // if (epoch > 200) reward *= 2;
                        }

                        // else if (optimizing && currentState[11] > oldState[11]) reward += -moveCloserReward / 2d;

                        // System.out.println("Closest Distance: " + closestDistance + " Max Distance: " + maxDistance);
                        // System.out.println("Current Distance: " + currentState[11]);
                        // System.out.println("Reward: " + reward);

                        totalReward += reward;

                        if (totalReward == collisionReward) break;

                        agent.storeExperience(oldState, action, reward, currentState, gameFinished || gameWon);
                        agent.train();

                        moveNumber++;
                        if (moveNumber >= 1000 || shouldReset) break;

                        times[moveNumber] = System.currentTimeMillis() - startTime;
                    }
                    epoch++;
                    if (optimizing) System.out.println("Total Reward Optimizing: " + totalReward);
                    else System.out.println("Total Reward: " + totalReward);
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
                                for (int i = 0; i < 5; i++) accelerate();
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
                        if (gameFinished) {
                            testing = false;
                            totalTimesFinished = 0;
                            break;   
                        }
                        if (gameWon && epoch > 400) {
                            totalTimesFinished++;
                            testing = false;
                        }
                        else if (gameWon) {
                            testing = false;
                            break;
                        }
                        if (totalTimesFinished >= 3 && !optimizing) {
                            saveModel();
                            optimizing = true;
                            testing = false;
                            agent.resetEpsilon(.3);
                            break;
                        }
                        if (moveNumber >= 1000) {
                            testing = false;
                            break;
                        }
                    }
                    reset(false);
                    moveNumber = 0;
                }   
            }
        });
        thread.start();
    }

    public void saveModel() {
        File modelFile = new File("C:\\Users\\rohan\\Desktop\\RPATEL\\DQN Files\\dqnModel.zip");
        try {
            ModelSerializer.writeModel(agent.getNeuralNet(), modelFile, true);
        } catch (IOException ignored) {
        }

    }

    public void loadModel() {
        File modelFile = new File("C:\\Users\\rohan\\Desktop\\RPATEL\\DQN Files\\dqnModel.zip");
        MultiLayerNetwork restoredNetwork = null;
        try {
            restoredNetwork = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        } catch (IOException ignored) {
        }
        if (restoredNetwork != null) agent.setNeuralNet(restoredNetwork);
        agent.resetEpsilon(1);
        testing = true;
        reset(false);
    }
}

