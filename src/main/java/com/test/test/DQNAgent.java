package com.test.test;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Random;

public class DQNAgent {
    private double epsilon;
    private double epsilonMin;
    private double epsilonDecay;
    private DQNNetwork network;
    private ExperienceReplay replayBuffer;
    private double gamma;
    private Random random;
    private int actionSize;
    private int updateTargetFrequency;
    private int updateCount;

    public DQNAgent(int stateSize, int actionSize, int hiddenSize, int replayCapacity) {
        this.network = new DQNNetwork(stateSize, hiddenSize, actionSize);
        this.replayBuffer = new ExperienceReplay(replayCapacity);
        this.epsilon = 1.0;
        this.epsilonMin = 0.01;
        this.epsilonDecay = 0.995;
        this.gamma = 0.95;
        this.random = new Random();
        this.actionSize = actionSize;
        this.updateTargetFrequency = 1000;
        this.updateCount = 0;
    }

    public int chooseAction(double[] state) {
        if (random.nextDouble() < epsilon) {
            return random.nextInt(actionSize);
        } else {
            INDArray qValues = network.predict(state);
            return Nd4j.argMax(qValues).getInt(0);
        }
    }

    public int chooseBestAction(double[] state) {
        INDArray qValues = network.predict(state);
        return Nd4j.argMax(qValues).getInt(0);
    }

    public void storeExperience(double[] state, int action, double reward, double[] nextState, boolean done) {
        replayBuffer.add(new Experience(state, action, reward, nextState, done));
    }

    public void train() {
        if (replayBuffer.size() < 32) return;

        List<Experience> batch = replayBuffer.sample(32);

        for (Experience exp : batch) {
            double[] target = network.predict(exp.state).toDoubleVector();
            if (exp.done) {
                target[exp.action] = exp.reward;
            } else {
                double[] nextQ = network.targetPredict(exp.nextState).toDoubleVector();
                target[exp.action] = exp.reward + gamma * Nd4j.max(Nd4j.create(nextQ)).getDouble(0);
            }
            network.train(exp.state, target);
        }

        updateCount++;
        if (updateCount % updateTargetFrequency == 0) {
            network.updateTargetNetwork();
        }

        if (epsilon > epsilonMin) {
            epsilon *= epsilonDecay;
        }
    }
}