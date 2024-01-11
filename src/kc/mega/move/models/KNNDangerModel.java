package kc.mega.move.models;

import kc.mega.model.WaveKNN;
import kc.mega.wave.GFBins;
import kc.mega.wave.WaveWithFeatures;

import java.util.List;

import ags.utils.KdTree.Entry;

/** KNN model for estimating waveSurfing dangers */
public class KNNDangerModel extends DangerModel {
  public final WaveKNN<Double> knn;
  public final GFBins bins;

  public KNNDangerModel(String name, GFBins bins, WaveKNN<Double> knn) {
    super(name);
    this.knn = knn;
    this.bins = bins;
  }

  @Override
  public void train(WaveWithFeatures w) {
    knn.addPoint(w, w.hitGF());
  }

  @Override
  public double[] getDangers(WaveWithFeatures w) {
    List<Entry<Double>> neighbors = knn.getNeighbors(w);
    double[] dangers = new double[bins.nBins];
    if (neighbors.isEmpty()) {
      return dangers;
    }
    double[] weights = knn.getWeights(neighbors);
    for (int i = 0; i < neighbors.size(); i++) {
      dangers[bins.getBin(neighbors.get(i).value)] += weights[i];
    }
    return dangers;
  }
}
