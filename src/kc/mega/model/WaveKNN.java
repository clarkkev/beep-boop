package kc.mega.model;

import kc.mega.utils.MathUtils;
import kc.mega.wave.WaveWithFeatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ags.utils.KdTree;
import ags.utils.KdTree.Entry;
import jk.math.FastTrig;

/** KNN lookup for featurized waves built on top of Rednaxela's KD Tree implementation. */
public class WaveKNN<T> {
  private final KdTree<T> tree;
  private final String[] features;
  private final double[][] params;
  private final double distanceScale;
  private final int maxNeighbors;
  private final double neighborhoodSizeDivider;
  private final List<double[][]> neuralNet;
  private final List<double[]> activations;

  public static class Builder<T> {
    private String[] features;
    private double[][] params;
    private double distanceScale;
    private double neighborhoodSizeDivider;
    private int maxNeighbors;
    private int maxTreeSize = 50000;
    private String neuralNet;

    public Builder<T> features(String[] val) {features = val; return this;}
    public Builder<T> params(double[][] val) {params = val; return this;}
    public Builder<T> distanceScale(double val) {distanceScale = val; return this;}
    public Builder<T> neighborhoodSizeDivider(double val) {neighborhoodSizeDivider = val; return this;}
    public Builder<T> maxNeighbors(int val) {maxNeighbors = val; return this;}
    public Builder<T> maxTreeSize(int val) {maxTreeSize = val; return this;}
    public Builder<T> nn(String val) {neuralNet = val; return this;}
    public WaveKNN<T> build() {return new WaveKNN<T>(this);}
  }

  public WaveKNN(Builder<T> builder) {
    tree = new KdTree.Manhattan<>(builder.features.length, builder.maxTreeSize);
    this.features = builder.features;
    this.params = builder.params;
    this.distanceScale = builder.distanceScale;
    this.maxNeighbors = builder.maxNeighbors;
    this.neighborhoodSizeDivider = builder.neighborhoodSizeDivider;
    neuralNet = new ArrayList<>();
    activations = new ArrayList<>();
    if (builder.neuralNet != null) {
      for (String matrixData: builder.neuralNet.split("\n")) {
        String[] rows = matrixData.split(",");
        double[][] M = new double[rows.length][rows[0].split(" ").length];
        for (int i = 0; i < rows.length; i++) {
          String[] row = rows[i].strip().split(" ");
          for (int j = 0; j < row.length; j++) {
            M[i][j] = Double.valueOf(row[j]);
          }
        }
        neuralNet.add(M);
        activations.add(new double[M[0].length]);
      }
    }
  }

  public void addPoint(WaveWithFeatures w, T value) {
    tree.addPoint(embed(w), value);
  }

  public List<Entry<T>> getNeighbors(WaveWithFeatures w) {
    return tree.nearestNeighbor(embed(w), getNumNeighbors(), false);
  }

  public List<Entry<T>> getNeighbors(WaveWithFeatures w, int numNeighbors) {
    return tree.nearestNeighbor(embed(w), numNeighbors, false);
  }

  public int getNumNeighbors() {
    return Math.min(maxNeighbors, Math.max(5, (int)(tree.size() / neighborhoodSizeDivider)));
  }

  public boolean isEmpty() {
    return tree.size() > 0;
  }

  public double[] embed(WaveWithFeatures w) {
    return embed(getNormalizedFeatures(w));
  }

  private double[] embed(Map<String, Double> normalizedFeatures) {
    double[] embedding = new double[features.length];
    for (int i = 0; i < features.length; i++) {
      double featureVal = normalizedFeatures.get(features[i]);
      embedding[i] = params[i].length == 1 ? params[i][0] * featureVal :
        params[i][0] * Math.pow(1e-4 + params[i][1] + featureVal, params[i][2]);
    }

    if (!neuralNet.isEmpty()) {
      // simple MLP neural network
      double[] h = embedding;
      for (int layer = 0; layer < neuralNet.size(); layer++) {
        double[][] M = neuralNet.get(layer);
        double[] newH = activations.get(layer);
        for (int i = 0; i < h.length; i++) {
          for (int j = 0; j < newH.length; j++) {
            if (i == 0) {
              newH[j] = M[i][j] * h[i];
            } else {
              newH[j] += M[i][j] * h[i];
            }
          }
        }
        // ReLU activation
        if (layer != neuralNet.size() - 1) {
          for (int i = 0; i < newH.length; i++) {
            newH[i] = Math.max(0, newH[i]);
          }
        }
        h = newH;
      }
      for (int i = 0; i < embedding.length; i++) {
        embedding[i] *= h[i];
      }
    }

    return embedding;
  }

  public double[] getWeights(List<Entry<T>> neighbors) {
    // softmax-rescaled distances to neighbors
    double[] weights = new double[neighbors.size()];
    double maxLogit = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < neighbors.size(); i++) {
      double distance = neighbors.get(i).distance;
      double logit = distance * distanceScale;
      weights[i] = logit;
      maxLogit = Math.max(logit, maxLogit);
    }
    MathUtils.softmax(weights, maxLogit);
    return weights;
  }

  public static Map<String, Double> getNormalizedFeatures(WaveWithFeatures w) {
    double bft = w.distance / w.speed;
    double latVel = w.velocity * FastTrig.sin(w.relativeHeading);
    double advVel = w.velocity * FastTrig.cos(w.relativeHeading);
    double advDir = w.moveDirection * FastTrig.cos(w.relativeHeading);
    Map<String, Double> normalizedFeatures = new HashMap<>();
    normalizedFeatures.put("virtuality", w.virtuality / 5);
    normalizedFeatures.put("power", w.power / 3);
    normalizedFeatures.put("bft", bft / 100);
    normalizedFeatures.put("accel", Math.max(2 + w.accel, 0) / 2);
    normalizedFeatures.put("accelSign", Math.signum(w.accel));
    normalizedFeatures.put("latVel", Math.abs(latVel) / 8);
    normalizedFeatures.put("vel", Math.abs(w.velocity) / 8);
    normalizedFeatures.put("vel=8", Math.abs(w.velocity) > 7.9 ? 1.0 : 0.0);
    normalizedFeatures.put("advVel", (advVel + 16) / 8);
    normalizedFeatures.put("advDir", (advDir + 1) / 2);
    normalizedFeatures.put("vChangeTimer", Math.min(w.vChangeTimer, 70) / bft);
    normalizedFeatures.put("dirChangeTimer", Math.min(w.dirChangeTimer, 70) / bft);
    normalizedFeatures.put("decelTimer", Math.min(w.decelTimer, 70) / bft);
    normalizedFeatures.put("distanceLast10", w.distanceLast10 / 80);
    normalizedFeatures.put("distanceLast20", w.distanceLast20 / 160);
    normalizedFeatures.put("mirrorOffset", w.mirrorOffset + Math.PI);
    normalizedFeatures.put("orbitalWallAhead", w.orbitalWallAhead / 1.5);
    normalizedFeatures.put("orbitalWallReverse", w.orbitalWallReverse / 1.5);
    normalizedFeatures.put("maeWallAhead", w.maeWallAhead);
    normalizedFeatures.put("maeWallReverse", w.maeWallReverse);
    normalizedFeatures.put("stickWallAhead", w.stickWallAhead / (Math.PI / 2));
    normalizedFeatures.put("stickWallReverse", w.stickWallReverse / (Math.PI / 2));
    normalizedFeatures.put("stickWallAhead2", w.stickWallAhead2 / (Math.PI / 2));
    normalizedFeatures.put("stickWallReverse2", w.stickWallReverse2 / (Math.PI / 2));
    normalizedFeatures.put("stickWallAhead=0", w.stickWallAhead < 0.001 ? 1.0 : 0.0);
    normalizedFeatures.put("stickWallReverse=0", w.stickWallReverse < 0.001 ? 1.0 : 0.0);
    normalizedFeatures.put("gameTime", w.fireTime / 500.0);
    normalizedFeatures.put("shotsFired", w.shotsFired / 1000.0);
    normalizedFeatures.put("currentGF", (1 + w.currentGF) / 2);
    normalizedFeatures.put("didHit", w.didHit ? 1.0 : 0.0);
    normalizedFeatures.put("didCollide", w.didCollide ? 1.0 : 0.0);
    return normalizedFeatures;
  }
}
