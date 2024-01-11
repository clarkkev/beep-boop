package kc.mega.move;

import kc.mega.game.GameState;
import kc.mega.game.HitRateTracker;
import kc.mega.move.models.DangerModel;
import kc.mega.move.models.DangerModels;
import kc.mega.move.wave.MovementWave;
import kc.mega.move.wave.MovementWaves;
import kc.mega.shared.Strategy;
import kc.mega.utils.DatasetWriter;
import kc.mega.utils.Geom;
import kc.mega.utils.MathUtils;
import kc.mega.utils.MovingAverage;
import kc.mega.utils.Range;
import kc.mega.wave.GFBins;
import kc.mega.wave.Wave;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manages the ensemble of models used for BeepBoop's wave surfing danger estimation. */
public class DangerEstimator {
  private static final double KERNEL_LAMBDA = 13; // controls the bin smoothing width
  private static final double SHADOW_DISCOUNT = 0.98; // don't 100% trust bullet shadows
  // hyperparameters for dynamic weighting of ensemble models
  private static final double WEIGHT_ROLLING = 0.98;
  private static final double FORCE_ACTIVE_WEIGHT = 1.33;
  private static final double WEIGHT_POWER = 3;

  private final GFBins bins = Wave.BINS;
  private final Strategy strategy;
  private final HitRateTracker hitRateTracker;
  private final Map<MovementWave, DangerTracker> waveDangerTrackers;
  private final List<Estimator> activeEstimators;
  private final List<Estimator> estimators;
  private final Map<String, Double> antiRamMultipliers;

  public DangerEstimator(Strategy strategy, HitRateTracker hitRateTracker) {
    this.strategy = strategy;
    this.hitRateTracker = hitRateTracker;
    waveDangerTrackers = new HashMap<>();
    activeEstimators = new ArrayList<>();

    // weights and hit rate thresholds were learned offline
    estimators = Arrays.asList(
        // simulated targeting methods
        new Estimator(DangerModels.hot(bins), 0, 0.5),
        new Estimator(DangerModels.linear(bins), 0.0, 0.07, 0.15),
        new Estimator(DangerModels.linearWithWalls(bins), 0.0, 0.07, 0.3),
        new Estimator(DangerModels.circular(bins), 0.0, 0.07, 0.25),
        new Estimator(DangerModels.avgLinear(bins), 1, 0.3),
        new Estimator(DangerModels.nanoLinearFixedSpeed(bins), 0.0, 0.07, 0.2),
        new Estimator(DangerModels.currentGF(bins), 1, 0.3),
        // KNN models trained against various bots
        new Estimator(DangerModels.simple(bins), 0.0, 0.75),
        new Estimator(DangerModels.simple2(bins), 0.06, 0.75),
        new Estimator(DangerModels.thorn(bins), 0.06, 2.0),
        new Estimator(DangerModels.druss(bins), 0.08, 2.0),
        new Estimator(DangerModels.diamond(bins), 0.07, 1.5),
        new Estimator(DangerModels.komarious(bins), 0.05, 1.0),
        new Estimator(DangerModels.splinter(bins), 0.0, 0.5),
        new Estimator(DangerModels.waveShark(bins), 0.03, 3.5),
        new Estimator(DangerModels.microAspid(bins), 0.03, 1.5),
        new Estimator(DangerModels.sedan(bins), 0.08, 1.5),
        // flattener KNN models (https://robowiki.net/wiki/Flattener)
        new Estimator(DangerModels.flattener1(bins), 0.09, 2.0, false),
        new Estimator(DangerModels.flattener2(bins), 0.09, 2.0, false),
        new Estimator(DangerModels.flattener3(bins), 0.02, 2.5, false),
        new Estimator(DangerModels.flattener4(bins), 0.02, 3.0, false),
        new Estimator(DangerModels.apmFlattener(bins), 0.06, 0.5, false)
    );
    antiRamMultipliers = Map.of("Circular", 1.0, "Linear", 1.0, "Sedan", 1.0,
                                "NanoLinearFixedSpeed", 0.5);
  }

  public void onTurn(GameState gs, MovementWaves movementWaves) {
    List<MovementWave> surfableWaves = movementWaves.getSurfableWaves(gs.myState);
    for (MovementWave w : new ArrayList<>(waveDangerTrackers.keySet())) {
      if (!surfableWaves.contains(w)) {
        waveDangerTrackers.remove(w);
      }
    }
    for (DangerTracker dangerTracker : waveDangerTrackers.values()) {
      dangerTracker.rangeDangers.clear();
    }
    for (MovementWave w : movementWaves.updatedShadowWaves) {
      if (waveDangerTrackers.containsKey(w)) {
        waveDangerTrackers.get(w).onUpdatedShadow();
      }
    }
    movementWaves.updatedShadowWaves.clear();
    for (Estimator estimator : estimators) {
      estimator.model.onTurn(gs);
    }
    setActiveEstimators();
  }

  public void onSeeBullet(MovementWave w) {
    if (!w.didCollide) {
      DatasetWriter.INSTANCE.write("move-data-dangers", new double[] {
          getDangersWithoutShadows(w)[bins.getBin(w.hitGF())]});
      DatasetWriter.INSTANCE.write("move-data-waves", w.toArray());
    }
    for (Estimator estimator : estimators) {
      estimator.updateWeightingStats(w);
    }
    for (Estimator estimator : estimators) {
      if (estimator.isHitModel) {
        estimator.model.train(w);
        for (Map.Entry<MovementWave, DangerTracker> e : waveDangerTrackers.entrySet()) {
          if (e.getKey() != w && !e.getKey().isVirtual) {
            e.getValue().onUpdatedEstimator(estimator);
          }
        }
      }
    }
  }

  public void onVisit(MovementWave w) {
    for (Estimator estimator : estimators) {
      if (!estimator.isHitModel) {
        estimator.model.train(w);
      }
    }
  }

  private DangerTracker getDangerTracker(MovementWave w) {
    DangerTracker dangerTracker = waveDangerTrackers.get(w);
    if (dangerTracker == null) {
      dangerTracker = new DangerTracker(w);
      waveDangerTrackers.put(w, dangerTracker);
    }
    return dangerTracker;
  }

  public double[] getDangers(MovementWave w) {
    return getDangerTracker(w).getDangers();
  }

  public double[] getDangersWithoutShadows(MovementWave w) {
    getDangers(w);
    double[] dangers = getDangerTracker(w).combinedDangers.clone();
    MathUtils.normalize(dangers);
    return dangers;
  }

  public double getDanger(MovementWave w, Range GFRange) {
    DangerTracker dangerTracker = getDangerTracker(w);
    if (dangerTracker.rangeDangers.containsKey(GFRange)) {
      return dangerTracker.rangeDangers.get(GFRange);
    }
    Range alreadyHitGFRange = !w.visitOffsetRange.isEmpty() ? w.hitGFRange() : null;
    double[] dangers = getDangers(w);
    double danger = 0;
    for (int bin = bins.getBin(GFRange.start); bin <= bins.getBin(GFRange.end); bin++) {
      double binWeight = bins.binWeight(GFRange, bin);
      // if part of the wave has already hit us, don't add danger from it
      if (alreadyHitGFRange != null && alreadyHitGFRange.start < bins.upperGF[bin] &&
          alreadyHitGFRange.end > bins.lowerGF[bin]) {
        binWeight *= 1 - bins.binWeight(alreadyHitGFRange, bin);
      }
      danger += dangers[bin] * binWeight;
    }
    danger = Math.max(danger, 0);
    dangerTracker.rangeDangers.put(GFRange, danger);
    return danger;
  }

  public double getApproximateDanger(MovementWave w, Point2D.Double location) {
    // approximate danger estimate to a point when we don't have the GF range
    double halfWidth = Geom.maxHalfBotAngle(w.source.distance(location));
    halfWidth /= Math.min(w.maePrecise[0], w.maePrecise[1]);
    int halfWidthBins = (int)Math.ceil(halfWidth / bins.binWidth);
    int bin = bins.getBin(w.getGF(location));
    double danger = 0;
    double[] dangers = getDangers(w);
    for (int i = Math.max(0, bin - halfWidthBins);
        i <= Math.min(bins.nBins - 1, bin + halfWidth); i++) {
      danger += dangers[i];
    }
    return danger * halfWidth / (halfWidthBins * bins.binWidth);
  }

  public static class ApproximateDangerLocation {
    public double danger = Double.POSITIVE_INFINITY;
    public double startGF;
    public double endGF;
    public double width;
  }

  public ApproximateDangerLocation getBestApproximateDanger(
      MovementWave w, double distance, Range range) {
    // find the lowest-danger region within a guessfactor range
    double width = Geom.maxBotAngle(distance) / Math.min(w.maePrecise[0], w.maePrecise[1]);
    int widthBins = (int)Math.ceil(width / bins.binWidth);
    // TODO: this divide by two introduces rounding issues
    int startBin = Math.max(0, bins.getBin(range.start) - widthBins / 2);
    int endBin = Math.min(bins.nBins - 1, bins.getBin(range.end) + widthBins / 2);
    double[] dangers = getDangers(w);
    ApproximateDangerLocation loc = new ApproximateDangerLocation();
    int locEnd = 0;
    double danger = 0;
    for (int i = startBin; i <= endBin; i++) {
       danger += dangers[i];
       if (i >= startBin + widthBins) {
         danger -= dangers[i - widthBins];
       }
       if ((i >= startBin + widthBins || i == endBin) && danger < loc.danger) {
         loc.danger = danger;
         locEnd = i;
       }
    }
    loc.startGF = bins.lowerGF[Math.max(0, locEnd - widthBins)];
    loc.endGF = bins.upperGF[locEnd];
    loc.width = width;
    loc.danger *= width / (widthBins * bins.binWidth);
    return loc;
  }

  public void paint() {
    for (Map.Entry<MovementWave, DangerTracker> e : waveDangerTrackers.entrySet()) {
      MovementWave w = e.getKey();
      if (w.hasBullet && !w.didHit && !w.isSimulated && w.radius > 0) {
        w.paintDangers(e.getValue().getDangers());
      }
    }
  }

  /** Caches wave dangers and just-in-time recomputes them when needed. */
  private class DangerTracker {
    private final MovementWave w;
    private final Map<Estimator, double[]> estimatorDangers;
    private final Map<Range, Double> rangeDangers;
    private double[] combinedDangers;
    private double[] shadowedDangers;

    private DangerTracker(MovementWave w) {
      this.w = w;
      estimatorDangers = new HashMap<>();
      rangeDangers = new HashMap<>();
      combinedDangers = null;
      shadowedDangers = null;
    }

    public double[] getDangers() {
      // get dangers after bin smoothing and bullet shadows
      if (shadowedDangers != null) {
        return shadowedDangers;
      }
      if (combinedDangers == null) {
        double[] unsmoothedDangers = w.isVirtual ? getAggregatedDangers(true) :
          MathUtils.addArrays(getAggregatedDangers(true), getAggregatedDangers(false));
        // https://robowiki.net/wiki/Bin_Smoothing
        combinedDangers = new double[bins.nBins];
        for (int i = 0; i < bins.nBins; i++) {
          if (unsmoothedDangers[i] > 0) {
            bins.updateBinsWithExpKernel(
                combinedDangers, bins.midPoint[i], KERNEL_LAMBDA, unsmoothedDangers[i]);
          }
        }
      }
      if (w.shadows == null) {
        shadowedDangers = combinedDangers;
      } else {
        // remove dangers from https://robowiki.net/wiki/Bullet_Shadow
        shadowedDangers = new double[bins.nBins];
        for (int i = 0; i < bins.nBins; i++) {
          shadowedDangers[i] = (1 - w.shadows[i] * SHADOW_DISCOUNT) * combinedDangers[i];
        }
      }
      MathUtils.normalize(shadowedDangers);
      return shadowedDangers;
    }

    private double[] getAggregatedDangers(boolean useHitModels) {
      // get the raw dangers from the estimators
      double[] unsmoothedDangers = new double[bins.nBins];
      for (Estimator estimator : activeEstimators) {
        double multiplier = (strategy.antiRam ? antiRamMultipliers.get(estimator.model.name) :
          estimator.multiplier) * (strategy.antiHOT && estimator.model.name.equals("HOT") ? 2 : 1);
        if (estimator.isHitModel == useHitModels && (!strategy.antiRam || !w.isSimulated ||
            estimator.model.name.equals("Circular") ||
            estimator.model.name.equals("NanoLinearFixedSpeed"))) {
          double[] dangers = getEstimatorDangers(estimator);
          for (int i = 0; i < dangers.length; i++) {
            unsmoothedDangers[i] += dangers[i] * estimator.weight * multiplier;
          }
        }
      }
      return unsmoothedDangers;
    }

    private double[] getEstimatorDangers(Estimator estimator) {
      if (estimatorDangers.containsKey(estimator)) {
        return estimatorDangers.get(estimator);
      }
      double[] dangers = estimator.model.getDangers(w);
      MathUtils.normalize(dangers);
      estimatorDangers.put(estimator, dangers);
      return dangers;
    }

    public void onUpdatedShadow() {
      shadowedDangers = null;
    }

    public void onActiveEstimatorsChanged() {
      shadowedDangers = combinedDangers = null;
    }

    public void onUpdatedEstimator(Estimator estimator) {
      if (estimatorDangers.containsKey(estimator)) {
        shadowedDangers = combinedDangers = null;
        estimatorDangers.remove(estimator);
      }
    }
  }

  private void setActiveEstimators() {
    setEstimatorWeights(estimators);
    boolean activeEstimatorsChanged = false;
    for (Estimator estimator : estimators) {
      if (estimator.isActive() && !activeEstimators.contains(estimator)) {
        activeEstimators.add(estimator);
        activeEstimatorsChanged = true;
        //System.out.println("Adding danger estimator " + estimator.name);
      }
      if (!estimator.isActive() && activeEstimators.contains(estimator)) {
        activeEstimators.remove(estimator);
        activeEstimatorsChanged = true;
        //System.out.println("Removing danger estimator " + estimator.name);
      }
    }
    setEstimatorWeights(activeEstimators);
    if (activeEstimatorsChanged) {
      for (DangerTracker dangerTracker : waveDangerTrackers.values()) {
        dangerTracker.onActiveEstimatorsChanged();
      }
    }
  }

  private void setEstimatorWeights(List<Estimator> estimators) {
    double total = 0;
    for (Estimator estimator : estimators) {
      total += estimator.getUnnormalizedWeight();
    }
    for (Estimator estimator : estimators) {
      estimator.weight = estimator.getUnnormalizedWeight() / total;
    }
  }

  private void printEstimatorWeight(Estimator estimator, double total) {
    System.out.printf(" " + estimator.model.name + " %.3f\n",
        estimator.getUnnormalizedWeight() / (total / estimators.size()));
  }

  public void printEstimatorWeights() {
    double total = 0;
    for (Estimator estimator : estimators) {
      total += estimator.getUnnormalizedWeight();
    }
    System.out.println("Inactive estimators:");
    for (Estimator estimator : estimators) {
      if (!estimator.isActive()) {
        printEstimatorWeight(estimator, total);
      }
    }
    System.out.println("Active estimators:");
    for (Estimator estimator : activeEstimators) {
      printEstimatorWeight(estimator, total);
    }
  }

  /** Controls the weight of a danger model and decides when to turn it on or off. */
  private class Estimator {
    public final DangerModel model;
    public final boolean isHitModel;
    public final double minHitRate;
    public final double multiplier;
    public final double maxHitRate;
    public final MovingAverage avgBulletDanger;

    public int hits;
    public double weight;

    public Estimator(DangerModel model, double minHitRate, double multiplier) {
      this(model, minHitRate, multiplier, true);
    }

    public Estimator(DangerModel model, double minHitRate, double maxHitRate, double multiplier) {
      this(model, minHitRate, maxHitRate, multiplier, true);
    }

    public Estimator(DangerModel model, double minHitRate, double multiplier, boolean isHitModel) {
      this(model, minHitRate, 1, multiplier, isHitModel);
    }

    public Estimator(DangerModel model, double minHitRate, double maxHitRate, double multiplier,
        boolean isHitModel) {
      this.model = model;
      this.isHitModel = isHitModel;
      this.minHitRate = minHitRate;
      this.maxHitRate = maxHitRate;
      this.multiplier = multiplier;
      this.avgBulletDanger = new MovingAverage(WEIGHT_ROLLING);
    }

    public void updateWeightingStats(MovementWave w) {
      // update how successful the model is at predicting enemy bullets
      if (hits > 0) {  // ignore the first hit (as the model will have no data)
        //double preWeight = getUnnormalizedWeight();
        double[] dangers = getDangerTracker(w).getEstimatorDangers(this);
        double dangerEstimate = 1e-4;
        double halfWidth = Geom.maxHalfBotAngle(w.distance);
        halfWidth /= w.hitOffset * w.orbitDirection > 0 ? w.maePrecise[1] : w.maePrecise[0];
        Range GFRange = new Range(Math.max(-0.999, w.hitGF() - halfWidth),
                                  Math.min(0.999, w.hitGF() + halfWidth));
        int hitBin = bins.getBin(w.hitGF());
        int maxBinDiff = bins.expKernelWidth(KERNEL_LAMBDA);
        int minBin = Math.max(0, Math.min(bins.getBin(GFRange.start), hitBin - maxBinDiff));
        int maxBin = Math.min(bins.nBins - 1,
            Math.max(bins.getBin(GFRange.end), hitBin + maxBinDiff));
        for (int bin = minBin; bin <= maxBin; bin++) {
          if (dangers[bin] > 0) {
            // mix smoothed and unsmoothed dangers
            dangerEstimate += bins.binWeight(GFRange, bin) * dangers[bin];
            double distance = Math.abs(bin - hitBin) * bins.binWidth;
            dangerEstimate += 2 * Math.exp(distance * -KERNEL_LAMBDA) * dangers[bin];
          }
        }
        //datasetWriter.INSTANCE.write("collisions", new double[] {
        //    dangerEstimate, preWeight, hits});
        avgBulletDanger.update(dangerEstimate);
      }
      hits++;
    }

    public boolean isActive() {
      if (strategy.antiRam) {
        return antiRamMultipliers.containsKey(model.name);
      }
      return hitRateTracker.hitRateInBounds(minHitRate, maxHitRate) ||
          weight > FORCE_ACTIVE_WEIGHT / estimators.size();
    }

    public double getUnnormalizedWeight() {
      return hits <= 1 ? 1 : Math.pow(avgBulletDanger.get(), WEIGHT_POWER);
    }
  }
}
