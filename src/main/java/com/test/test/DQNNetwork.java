package com.test.test;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public class DQNNetwork {
    private MultiLayerNetwork model;
    private MultiLayerNetwork targetModel;

    public DQNNetwork(int inputSize, int hiddenSize, int outputSize) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(123)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(new Adam(0.001))
                .list()
                .layer(new DenseLayer.Builder()
                        .nIn(inputSize)
                        .nOut(hiddenSize)
                        .activation(Activation.RELU)
                        .weightInit(WeightInit.XAVIER)
                        .build())
                .layer(new DenseLayer.Builder()
                        .nIn(hiddenSize)
                        .nOut(hiddenSize)
                        .activation(Activation.RELU)
                        .weightInit(WeightInit.XAVIER)
                        .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .activation(Activation.IDENTITY)
                        .nIn(hiddenSize)
                        .nOut(outputSize)
                        .build())
                .build();

        model = new MultiLayerNetwork(conf);
        model.init();
        targetModel = new MultiLayerNetwork(conf.clone());
        targetModel.init();
        updateTargetNetwork();
    }

    public void updateTargetNetwork() {
        targetModel.setParams(model.params());
    }

    public INDArray predict(double[] state) {
        INDArray input = Nd4j.create(state).reshape(1, state.length);
        return model.output(input);
    }

    public INDArray targetPredict(double[] state) {
        INDArray input = Nd4j.create(state).reshape(1, state.length);
        return targetModel.output(input);
    }

    public void train(double[] state, double[] target) {
        INDArray input = Nd4j.create(state).reshape(1, state.length);
        INDArray label = Nd4j.create(target).reshape(1, target.length);
        model.fit(input, label);
    }

    public MultiLayerNetwork getModel() {
        return this.model;
    }

    public void setModel(MultiLayerNetwork model) {
        this.model = model;
        this.model.init();
        updateTargetNetwork();
    }
}