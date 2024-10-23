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
import java.util.Random;
import java.util.Timer;


public class PathfindingWebSocketHandler extends TextWebSocketHandler {
    private double playerX = 47;
    private double playerY = 47;
    private double[] dot = new double[2];
    private int numberOfDotsCollected;
    private boolean hasCollided = false;

    private int epoch = 0;
    private boolean gameFinished = false;
    private double[] currentState = new double[4];
    private double[] oldState;
    private double previousDistance;
    private double[] times = new double[2500];
    private int time = 30;
    private boolean shouldReset;
    private DQNAgent agent;

    private boolean running = false;
    private boolean testing = false;

    private double moveCloserReward;
    private double moveFartherReward;
    private double collisionReward;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSession webSocketSession;
    private Random random;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        webSocketSession = session;
        random = new Random();
        dot[0] = random.nextInt(30, 70);
        dot[1] = random.nextInt(30, 70);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        if (jsonNode.has("action")) {
            String action = jsonNode.get("action").asText();
            switch (action) {
                case "up":
                    up();
                    break;
                case "down":
                    down();
                    break;
                case "left":
                    left();
                    break;
                case "right":
                    right();
                    break;
                case "collision":
                    collision();
                    break;
                case "won":
                    break;
                case "save":
                    saveModel();
                    break;
                case "load":
                    loadModel();
                    break;
                case "epoch":
                    epoch = 372;
                    sendPlayerState();
                    break;
                case "run":
                    if (!running) {
                        running = true;
                        testing = false;

                        double[] rewards = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                        moveCloserReward = rewards[0];
                        moveFartherReward = rewards[1];
                        collisionReward = rewards[2];

                        runDQN();
                    }
                    break;
                case "test":
                    testing = !testing;
                    break;
                case "updateplayerPosition":
                    double[] playerPosition = objectMapper.convertValue(jsonNode.get("data"), double[].class);
                    playerX = playerPosition[0];
                    playerY = playerPosition[1];
                    break;
            }
            sendPlayerState();
        }
    }

    private synchronized void sendPlayerState() throws IOException{
        String formattedTime;
        if (time < 10) formattedTime = "0:0" + time;
        else formattedTime = "0:" + time;
        String response = String.format("{\"playerX\": %f, \"playerY\": %f, \"dotX\": %f, \"dotY\": %f, \"epoch\": %d, \"time\": \"%s\", \"numberOfDotsCollected\": %f}",
            playerX, playerY, dot[0], dot[1], epoch, formattedTime, (float) numberOfDotsCollected);
        webSocketSession.sendMessage(new TextMessage(response));
    }

    private void up() {
        if (playerY > 0) playerY -= 1;

    }

    private void down() {
        if (playerY < 97) playerY += 1;
    }

    private void left() {
        if (playerX > 0) playerX -= 1;

    }

    private void right() {
        if (playerX < 97) playerX += 1;
    }

    private void reset(boolean resetTimes) {
        playerX = 47;
        playerY = 47;
        gameFinished = false;
        if (resetTimes) for (int i = 0; i < 2500; i++) times[i] = 0;
        time = 30;
        numberOfDotsCollected = 0;
        dot[0] = random.nextInt(30, 70);
        dot[1] = random.nextInt(30, 70);
    }

    private void resetPlayer() {
        playerX = 47;
        playerY = 47;
    }

    private void collision() {
        hasCollided = true;
        
        dot[0] = random.nextInt(30, 70);
        dot[1] = random.nextInt(30, 70);
        numberOfDotsCollected++;

        try {
            sendPlayerState();
        } catch (IOException ignored) {
        }
    }

    public void saveModel() {
        File modelFile = new File("C:\\Users\\rohan\\Desktop\\RPATEL\\DQN Files\\pathfinding.zip");
        try {
            ModelSerializer.writeModel(agent.getNeuralNet(), modelFile, true);
        } catch (IOException ignored) {
        }

    }

    public void loadModel() {
        File modelFile = new File("C:\\Users\\rohan\\Desktop\\RPATEL\\DQN Files\\pathfinding.zip");
        MultiLayerNetwork restoredNetwork = null;
        try {
            restoredNetwork = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        } catch (IOException ignored) {
        }
        if (restoredNetwork != null) agent.setNeuralNet(restoredNetwork);
        testing = true;
        reset(false);
    }

    private double[] getState() {
        double[] state = new double[4];
        state[0] = playerX;
        state[1] = playerY;
        state[2] = dot[0] - playerX;
        state[3] = dot[1] - playerY;
        return state;
    }

    public double getDistance() {
        return Math.sqrt(Math.pow(dot[1] - playerY, 2) + Math.pow(dot[0] - playerX, 2));
    }

    private synchronized void runDQN() {
        running = true;
        int inputSize = currentState.length;
        int actionSize = 4;
        int hiddenSize = 6;
        int replayCapacity = 1000;

        Thread timerThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                time -= 1;

                if (time == 0 && !testing) {
                     reset(true);
                     shouldReset = true;
                }
                else if (time == 0) reset(false);
            }
        });
        timerThread.start();

        agent = new DQNAgent(inputSize, actionSize, hiddenSize, replayCapacity);
        Thread thread = new Thread(() -> {
            while (true) {
                double totalReward = 0;
                int moveNumber = 0;
                currentState = getState();
                if (!testing) {
                    while (!testing) {
                        double actionStartTime = System.currentTimeMillis();
                        previousDistance = getDistance();

                        int action = agent.chooseAction(currentState);
                        oldState = Arrays.copyOf(currentState, currentState.length);
                        switch (action) {
                            case 0 -> up();
                            case 1 -> down();
                            case 2 -> left();
                            case 3 -> right();
                        }

                        try {
                            sendPlayerState();
                            Thread.sleep(16);
                        } catch (Exception ignored) {
                        }

                        currentState = getState();

                        double reward;

                        if (getDistance() < previousDistance) reward = moveCloserReward / 10d;
                        else if (hasCollided) {
                            reward = collisionReward;
                            hasCollided = false;
                        } else if (currentState[0] <= 10 || currentState[0] >= 90 || currentState[1] <= 10 || currentState[1] >= 90) {
                            reward = -10;
                            resetPlayer();
                        } else reward = moveFartherReward / 10d;
                        
                        totalReward += reward;

                        agent.storeExperience(oldState, action, reward, currentState, gameFinished);
                        agent.train();
                        times[moveNumber] = System.currentTimeMillis() - actionStartTime;
                        moveNumber++;
                        if (shouldReset) break;
                    }
                    epoch++;
                    shouldReset = false;
                    System.out.println("Total Reward: " + totalReward);
                    if (!testing) reset(true);
                    else reset(false);
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    moveNumber = 0;
                    while (testing) {
                        currentState = getState();
                        int action = agent.chooseBestAction(currentState);
                        switch (action) {
                            case 0 -> up();
                            case 1 -> down();
                            case 2 -> left();
                            case 3 -> right();
                        }

                        currentState = getState();

                        if (currentState[0] <= 10 || currentState[0] >= 90 || currentState[1] <= 10 || currentState[1] >= 90) {
                            resetPlayer();
                        }

                        try {
                            sendPlayerState();
                            if (times[moveNumber] != 0) Thread.sleep((long) (times[moveNumber] - 1));
                            else Thread.sleep(52);
                        } catch (Exception e) {
                            System.out.println(times[moveNumber]);
                        }

                        moveNumber++;
                        if (gameFinished) testing = false;
                    }
                    reset(false);
                    moveNumber = 0;
                }
            }
        });
        thread.start();
    }
}

