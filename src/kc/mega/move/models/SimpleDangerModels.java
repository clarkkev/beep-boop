package kc.mega.move.models;

import kc.mega.game.BattleField;
import kc.mega.game.Physics;
import kc.mega.game.PredictState;
import kc.mega.utils.Geom;
import kc.mega.wave.GFBins;
import kc.mega.wave.WaveWithFeatures;

import jk.math.FastTrig;

/** Simulate simple targeting methods so we can dodge them perfectly. */
public class SimpleDangerModels {
  // https://robowiki.net/wiki/Head-On_Targeting
  public static class HOTModel extends DangerModel {
    private final GFBins bins;

    public HOTModel(GFBins bins) {
      super("HOT");
      this.bins = bins;
    }

    public double getAimAngle(WaveWithFeatures w) {
      return w.prevAbsoluteBearing;
    }

    @Override
    public double[] getDangers(WaveWithFeatures w) {
      double[] dangers = new double[bins.nBins];
      dangers[bins.getBin(w.getGF(w.prevAbsoluteBearing - w.absoluteBearing))] = 1;
      dangers[bins.getBin(w.getGF(w.prevAbsoluteBearing + w.fireState.heading -
          w.myState.heading - w.absoluteBearing))] += 0.5;
      dangers[bins.getBin(w.getGF(Geom.absoluteBearingPrecise(
          w.myState.getNextState(0).location, w.enemyState.location) - w.absoluteBearing))] += 0.5;
      dangers[bins.getBin(w.getGF(0))] += 0.5;
      for (int i = 0; i < bins.nBins; i++) {
        dangers[i] += 0.01 / (0.05 + Math.abs(bins.midPoint[i]));
      }
      return dangers;
    }
  }

  // last bearing offset / current GF targeting
  public static class CurrentGFModel extends DangerModel {
    private final GFBins bins;

    public CurrentGFModel(GFBins bins) {
      super("CurrentGF");
      this.bins = bins;
    }

    @Override
    public double[] getDangers(WaveWithFeatures w) {
      double[] dangers = new double[bins.nBins];
      dangers[bins.getBin(w.currentGF)] = 1;
      dangers[bins.getBin(-w.currentGF)] = 0.5;
      return dangers;
    }
  }

  public static abstract class SingleAngleModel extends DangerModel {
    private final GFBins bins;

    public SingleAngleModel(String name, GFBins bins) {
      super(name);
      this.bins = bins;
    }

    @Override
    public double[] getDangers(WaveWithFeatures w) {
      double GF = w.getGF(getAimAngle(w) - w.absoluteBearing);
      double[] dangers = new double[bins.nBins];
      bins.updateBinsWithExpKernel(dangers, GF, 20, 1);
      return dangers;
    }

    public abstract double getAimAngle(WaveWithFeatures w);
  }

  // non-iterative linear targeting
  // (https://robowiki.net/wiki/Linear_Targeting#Example_of_Noniterative_Linear_Targeting)
  public static class NanoLinearModel extends SingleAngleModel {
    public final boolean fixedBulletSpeed;

    public NanoLinearModel(GFBins bins, boolean fixedBulletSpeed) {
      super(fixedBulletSpeed ? "NanoLinearFixedSpeed" : "NanoLinear", bins);
      this.fixedBulletSpeed = fixedBulletSpeed;
    }

    @Override
    public double getAimAngle(WaveWithFeatures w) {
      return w.prevAbsoluteBearing + Math.asin(w.velocity * Math.sin(w.relativeHeading)
          / (fixedBulletSpeed ? 13 : w.speed));
    }
  }

  // iterative linear/circular/average-linear targeting (https://robowiki.net/wiki/Linear_Targeting)
  public static class LinearTargetingModel extends SingleAngleModel {
    private final boolean isCircular;
    private final boolean stopAtWall;
    private final boolean isAveraged;
    private final boolean predictVelocity;

    public LinearTargetingModel(String name, GFBins bins, boolean isCircular, boolean stopAtWalls) {
      this(name, bins, isCircular, stopAtWalls, false, false);
    }

    public LinearTargetingModel(String name, GFBins bins, boolean isCircular, boolean stopAtWalls,
        boolean isAveraged, boolean predictVelocity) {
      super(name, bins);
      this.isCircular = isCircular;
      this.stopAtWall = stopAtWalls;
      this.isAveraged = isAveraged;
      this.predictVelocity = predictVelocity;
    }

    @Override
    public double getAimAngle(WaveWithFeatures w) {
      double turnRate = isCircular ? FastTrig.normalRelativeAngle(
          w.enemyState.heading - w.prevEnemyState.heading) : 0;
      PredictState predictedState = w.enemyState.asPredictState();
      PredictState prevState = w.prevEnemyState.asPredictState();
      if (isAveraged) {
        predictedState = new PredictState(predictedState.location, predictedState.heading,
            w.avgVelocity, predictedState.gameTime);
      }
      int ticks = 0;
      while (++ticks * Physics.bulletSpeed(w.power) < predictedState.distance(w.myState)) {
        if (predictVelocity) {
          PredictState tmp = predictedState;
          predictedState = predictedState.predictNextState(prevState);
          prevState = tmp;
        } else {
          predictedState = predictedState.getNextState(turnRate);
        }
        if (stopAtWall && !BattleField.INSTANCE.getFieldRectangle().contains(
            predictedState.location)) {
          predictedState = BattleField.INSTANCE.wallCollisionCheck(predictedState);
          break;
        }
      }
      return w.myState.absoluteBearing(predictedState);
    }
  }
}
