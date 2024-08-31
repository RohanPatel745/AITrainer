package com.test.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class ExperienceReplay {
    private List<Experience> buffer;
    private int capacity;
    private Random random;

    public ExperienceReplay(int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayList<>();
        this.random = new Random();
    }

    public void add(Experience experience) {
        if (buffer.size() >= capacity) {
            buffer.remove(0);
        }
        buffer.add(experience);
    }

    public List<Experience> sample(int batchSize) {
        List<Experience> batch = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            batch.add(buffer.get(random.nextInt(buffer.size())));
        }
        return batch;
    }

    public int size() {
        return buffer.size();
    }
}

class Experience {
    double[] state;
    int action;
    double reward;
    double[] nextState;
    boolean done;

    public Experience(double[] state, int action, double reward, double[] nextState, boolean done) {
        this.state = state;
        this.action = action;
        this.reward = reward;
        this.nextState = nextState;
        this.done = done;
    }
}

