package com.test.test.maze;

import java.io.IOException;
import java.util.Random;

import org.bytedeco.leptonica.NUMA;

public class QLearningEnvironment {
    private static double epsilon = 1.0;
    private static double[][][] qTable;
    private static double learningRate = 0.1;
    private static double discountFactor = 0.99;
    private static double epsilonDecay = 0.995;
    private static double epsilonMin = 0.05;
    private static Random random;
    private static int currentX = 0;
    private static int currentY = 0;
    private static int epoch = 0;
    private static boolean shouldInterrupt = false;
    private static boolean shouldReset = false;
    private static int previousEnemy1X = 4;
    private static int previousEnemy2X = 0;

    private static final int MAZE_WIDTH = 5;
    private static final int MAZE_HEIGHT = 5;
    private static final int NUM_ACTIONS = 4;
    private static final int MAX_MOVES = 25;

    private static void init() {
        qTable = new double[MAZE_WIDTH * MAZE_HEIGHT][NUM_ACTIONS][MAX_MOVES];
        random = new Random();
    }

    public static int[] getCurrentState(int moveNumber) {
        return new int[]{currentX, currentY, moveNumber};
    }

    private static int chooseAction(int[] state, int moveNumber) {
        if (random.nextDouble() < epsilon) {
            return random.nextInt(NUM_ACTIONS);
        } else {
            double[] moves = new double[NUM_ACTIONS];
            for (int i = 0; i < NUM_ACTIONS; i++) {
                moves[i] = qTable[stateToIndex(state)][i][moveNumber];
            }
            return argMax(moves);
        }
    }

    private static int chooseBestAction(int[] state, int moveNumber) {
        double[] moves = new double[NUM_ACTIONS];
        for (int i = 0; i < NUM_ACTIONS; i++) {
            moves[i] = qTable[stateToIndex(state)][i][moveNumber];
        }
        return argMax(moves);    
    }

    private static int argMax(double[] values) {
        int bestAction = 0;
        double maxValue = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > maxValue) {
                bestAction = i;
                maxValue = values[i];
            }
        }
        return bestAction;
    }

    private static void updateQTable(int[] state, int action, int moveNumber, double reward, int[] nextState) {
        int stateIndex = stateToIndex(state);
        int nextStateIndex = stateToIndex(nextState);

        double[] moves = new double[NUM_ACTIONS];
        for (int i = 0; i < NUM_ACTIONS; i++) {
            moves[i] = qTable[nextStateIndex][i][moveNumber];
        }
        double maxNextQ = max(moves);
        qTable[stateIndex][action][moveNumber] = qTable[stateIndex][action][moveNumber] +
                learningRate * (reward + discountFactor * maxNextQ - qTable[stateIndex][action][moveNumber]);
    }

    private static int stateToIndex(int[] state) {
        return state[1] * MAZE_WIDTH + state[0];
    }

    private static double max(double[] values) {
        double maxValue = values[0];
        for (double value : values) {
            if (value > maxValue) {
                maxValue = value;
            }
        }
        return maxValue;
    }

    private static void decayEpsilon() {
        epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);
    }

    public static void train(int level, double... rewards) {
        init();
        while (!shouldInterrupt) {
            double totalReward = 0;
            for (int i = 0; i < MAX_MOVES; i++) {
                int[] state = getCurrentState(i);
                int action = chooseAction(state, i);

                performAction(action);
                try {
                    MazeWebSocketHandler.sendMazeState(getCurrentState(i));
                } catch (Exception ignored) {
                }

                int[] nextState = getCurrentState(i + 1);
                double reward = getReward(nextState, state, level, rewards);
                totalReward += reward;

                if (level == 1 || level == 2 || level == 4) {
                    if (reward == rewards[1]) {
                        if (totalReward == rewards[0] * 7 + rewards[1]) {
                            resetPosition();
                            for (int x = 0; x < 8; x++) {
                                state = getCurrentState(x);
                                action = chooseBestAction(state, x);

                                performAction(action);
                            }
                            if (getCurrentState(0)[0] == 4 && getCurrentState(0)[1] == 4) {
                                epoch++;
                                resetPosition();
                                test(level, rewards);
                                return;
                            }
                        }
                        break;
                    }
                } else if (level == 3) {
                    if (reward == rewards[1]) {
                        if (totalReward == rewards[0] * 11 + rewards[1]) {
                            resetPosition();
                            for (int x = 0; x < 12; x++) {
                                state = getCurrentState(x);
                                action = chooseBestAction(state, x);

                                performAction(action);
                            }
                            if (getCurrentState(0)[0] == 4 && getCurrentState(0)[1] == 4) {
                                epoch++;
                                resetPosition();
                                test(level, rewards);
                                return;
                            }
                        }
                        break;
                    }
                } else if (level == 5) {
                    if (reward == rewards[1]) {
                        if (totalReward == rewards[0] * 9 + rewards[1]) {
                            resetPosition();
                            for (int x = 0; x < 10; x++) {
                                state = getCurrentState(x);
                                action = chooseBestAction(state, x);

                                performAction(action);
                            }
                            if (getCurrentState(0)[0] == 4 && getCurrentState(0)[1] == 4) {
                                epoch++;
                                resetPosition();
                                test(level, rewards);
                                return;
                            }
                        }
                        break;
                    }
                }

                updateQTable(state, action, i, reward, nextState);
                try {
                    Thread.sleep(0, 0);
                } catch (Exception ignored) {
                }

                if (shouldInterrupt) return;
            }
            epoch++;
            decayEpsilon();
            resetPosition();
        }
    }

    public static void test(int level, double... rewards) {
        int moveNumber = 0;
        shouldInterrupt = false;
        while (!shouldInterrupt) {
            int[] state = getCurrentState(moveNumber);
            int action = chooseBestAction(state, moveNumber);
            performAction(action);
            moveNumber++;
            double reward = getReward(getCurrentState(moveNumber), state, level, rewards);
            try {
                MazeWebSocketHandler.sendMazeState(getCurrentState(moveNumber));
                Thread.sleep(250);
            } catch (Exception ignored) {
            }
            if (level == 1){
                if (reward == rewards[1]) {
                    resetPosition();
                    moveNumber = 0;
                    try {
                        MazeWebSocketHandler.sendMazeState(getCurrentState(moveNumber));
                        Thread.sleep(250);
                    } catch (Exception ignored) {
                    }
                }
            } else if (level == 2 || level == 3 || level == 4 || level == 5) {
                if (reward == rewards[1] || reward == rewards[2]) {
                    resetPosition();
                    moveNumber = 0;
                    try {
                        MazeWebSocketHandler.sendMazeState(getCurrentState(moveNumber));
                        Thread.sleep(250);
                    } catch (Exception ignored) {
                    }
                }
            }

            if (shouldInterrupt) return;
        }
    }

    private static void resetPosition() {
        currentX = 0;
        currentY = 0;
        shouldReset = true;
    }

    private static void performAction(int action) {
        switch (action) {
            case 0 -> {
                if (currentY > 0) currentY--;
            }
            case 1 -> {
                if (currentY < MAZE_HEIGHT - 1) currentY++;
            }
            case 2 -> {
                if (currentX > 0) currentX--;
            }
            case 3 -> {
                if (currentX < MAZE_WIDTH - 1) currentX++;
            }
        }
    }

    private static double getReward(int[] state, int[] previousState, int level, double... rewards) {
        if (level == 1) {
            if (state[0] == 4 && state[1] == 4) return rewards[1];
        } else if (level == 2) {
            if (state[0] == 4 && state[1] == 4) return rewards[1];
            else if ((state[0] == 0 || state[0] == 1 || state[0] == 3 || state[0] == 4) && state[1] == 2) return rewards[2];
        } else if (level == 3) {
            int[][] xSquares = new int[][]{{2, 0}, {2, 1}, {2, 2}, {3, 2}, {4, 2}, {0, 1}, {1, 3}, {3, 4}};
            if (state[0] == 4 && state[1] == 4) return rewards[1];
            for (int[] xSqaure : xSquares) {
                if (state[0] == xSqaure[0] && state[1] == xSqaure[1]) return rewards[2];
            }
        } else if (level == 4) {
            if (state[0] == 4 && state[1] == 4) return rewards[1];

            int enemyX = 0;
            if (state[2] % 8 == 1 || state[2] % 8 == 7) enemyX = 1;
            else if (state[2] % 8 == 2 || state[2] % 8 == 6) enemyX = 2;
            else if (state[2] % 8 == 3 || state[2] % 8 == 5) enemyX = 3;
            else if (state[2] % 8 == 4) enemyX = 4;

            if (state[0] == enemyX && state[1] == 2) return rewards[2];
        } else if (level == 5) {
            if (state[0] == 4 && state[1] == 4) return rewards[1];
            else if (((state[0] == 1 || state[0] == 2 || state[0] == 3 || state[0] == 4) && state[1] == 0)
            || ((state[0] == 0 || state[0] == 1 || state[0] == 2 || state[0] == 3) && state[1] == 3)) return rewards[2];

            int enemy1X = 4;
            if (state[2] % 8 == 1 || state[2] % 8 == 7) enemy1X = 3;
            else if (state[2] % 8 == 2 || state[2] % 8 == 6) enemy1X = 2;
            else if (state[2] % 8 == 3 || state[2] % 8 == 5) enemy1X = 1;
            else if (state[2] % 8 == 4) enemy1X = 0;

            int enemy2X = 0;
            if (state[2] % 8 == 1 || state[2] % 8 == 7) enemy2X = 1;
            else if (state[2] % 8 == 2 || state[2] % 8 == 6) enemy2X = 2;
            else if (state[2] % 8 == 3 || state[2] % 8 == 5) enemy2X = 3;
            else if (state[2] % 8 == 4) enemy2X = 4;

            if (((state[0] == enemy1X) || ((state[0] == previousEnemy1X) && previousState[0] == enemy1X)) && state[1] == 1) {
                previousEnemy1X = enemy1X;
                previousEnemy2X = enemy2X;
                return rewards[3];
            } else if (((state[0] == enemy2X) || ((state[0] == previousEnemy2X) && previousState[0] == enemy2X)) && state[1] == 2) {
                previousEnemy1X = enemy1X;
                previousEnemy2X = enemy2X;
                return rewards[3];
            }
        }
        return rewards[0];
    }

    public static int getEpoch() {
        return epoch;
    }

    public static void resetEpoch() {
        epoch = 0;
    }

    public static void setShouldInterrupt(boolean shouldInterrupt) {
        QLearningEnvironment.shouldInterrupt = shouldInterrupt;
    }

    public static void reset() {
        epsilon = 1.0;
        qTable = new double[MAZE_WIDTH * MAZE_HEIGHT][NUM_ACTIONS][MAX_MOVES];
        epoch = 0;
        resetPosition();
        try {
            MazeWebSocketHandler.sendMazeState(getCurrentState(0));
        } catch (Exception ignored) {
        }
    }

    public static boolean shouldReset() {
        if (shouldReset) {
            shouldReset = false;
            return true;
        }
        return false;
    }
}
