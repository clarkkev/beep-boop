package kc.mega.aim.models;

import kc.mega.model.Model;
import kc.mega.model.WaveKNN;
import kc.mega.utils.Painter;
import kc.mega.utils.Range;
import kc.mega.wave.WaveWithFeatures;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import ags.utils.KdTree.Entry;

/** KNN model for aiming; stores guessfactor ranges that would hit the opponent. */
public class KNNAimModel extends Model {
  private final WaveKNN<Range> knn;
  private VisitEdge[] edges;

  public KNNAimModel(String name, WaveKNN<Range> knn) {
    super(name);
    this.knn = knn;
  }

  public boolean hasData() {
    return knn.isEmpty();
  }

  @Override
  public void train(WaveWithFeatures w) {
    knn.addPoint(w, w.hitGFRange());
  }

  public double getAimGFFast(WaveWithFeatures w) {
    return getAimGF(w, knn.getNumNeighbors() / 2);
  }

  private double getAimGF(WaveWithFeatures w, int numNeighbors) {
    List<Entry<Range>> neighbors = knn.getNeighbors(w, numNeighbors);
    if (neighbors.isEmpty()) {
      return 0;
    }

    // sorting-based algorithm for finding the highest density guessFactor
    double[] weights = knn.getWeights(neighbors);
    double totalWeight = 0;
    edges = new VisitEdge[2 * neighbors.size()];
    for (int i = 0; i < neighbors.size(); i++) {
      Range GFRange = neighbors.get(i).value;
      double midPoint = GFRange.middle();
      edges[2 * i] = new VisitEdge(GFRange.start, midPoint, weights[i]);
      edges[1 + 2 * i] = new VisitEdge(GFRange.end, midPoint, -weights[i]);
      totalWeight += weights[i];
    }
    Arrays.sort(edges);
    double height = 0;
    double bestHeight = 0;
    int bestIndex = 0;
    for (int i = 0; i < edges.length; i++) {
      height += edges[i].weight;
      edges[i].prob = height / totalWeight;
      if (height > bestHeight) {
        bestHeight = height;
        bestIndex = i;
      }
    }
    return (edges[bestIndex].GF + edges[bestIndex + 1].GF) / 2;
  }

  public List<Double> getAimGFs(WaveWithFeatures w, int numAngles) {
    double bestGF = getAimGF(w, knn.getNumNeighbors());
    if (edges == null || numAngles == 1) {
      return new ArrayList<>(Arrays.asList(bestGF));
    }
    PriorityQueue<VisitEdge> q = new PriorityQueue<>(numAngles, new Comparator<VisitEdge>() {
      @Override
      public int compare(VisitEdge e1, VisitEdge e2) {
        return (int)Math.signum(e2.sampleScore - e1.sampleScore);
      }
    });
    for (int i = 0; i < edges.length; i += 2) {
      // Gumbel max trick to sample random visit guessfactors
      edges[i].sampleScore = Math.log(edges[i].weight) - Math.log(-Math.log(Math.random()));
      q.add(edges[i]);
      if (q.size() > numAngles - 1) {
        q.poll();
      }
    }
    List<Double> aimGFs = q.stream().map(e -> e.middle).collect(Collectors.toList());
    aimGFs.add(0, bestGF);
    return aimGFs;
  }

  public double scoreAimGF(double GF) {
    if (edges == null) {
      return 0.5;
    }
    int i = Arrays.binarySearch(edges, new VisitEdge(GF, 0, 0));
    if (i > 0) {
      return edges[i].prob;
    }
    i = -i - 1;
    if (i == 0) {
      return 0;
    }
    return edges[i - 1].prob;
  }

  public void paint() {
    if (edges != null) {
      for (int i = 1; i < edges.length; i++) {
        Painter.CUSTOM.addShape(Painter.TRANSLUCENT_RED, new Rectangle2D.Double(
            130 + 100 * edges[i - 1].GF, 0,
            100 * (edges[i].GF - edges[i - 1].GF), 100 * edges[i - 1].prob), true);
      }
    }
  }

  public static class VisitEdge implements Comparable<VisitEdge> {
    public double GF;
    public double middle;
    public double weight;
    public double prob;
    public double sampleScore;

    public VisitEdge(double GF, double middle, double weight) {
      this.GF = GF;
      this.middle = middle;
      this.weight = weight;
    }

    @Override
    public int compareTo(VisitEdge e){
      return (int)Math.signum(GF - e.GF);
    }
  }
}
