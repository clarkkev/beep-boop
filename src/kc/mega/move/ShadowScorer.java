package kc.mega.move;

import kc.mega.move.wave.MovementWave;
import kc.mega.utils.Geom;
import kc.mega.utils.Range;
import kc.mega.wave.Bullet;
import kc.mega.wave.GFBins;
import kc.mega.wave.Wave;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Scores firing guessfactors based on how much their bullet shadows reduce surfing danger. */
public class ShadowScorer {
  public final PathSurfer surfer;

  public ShadowScorer(PathSurfer surfer) {
    this.surfer = surfer;
  }

  public List<Double> getHelpfulShadowGFs(Wave w) {
    // find guessfactors that add shadows protecting our current movement plan
    List<Double> GFs = new ArrayList<>();
    for (PathSurfer.Node n : surfer.currentPlan) {
      if (n.surfWave == null) {
        continue;
      }
      w.trackPreciseShadows = true;
      w.shadow = null;
      w.addBulletShadows(new Bullet(
          n.surfWave, Geom.absoluteBearing(n.surfWave.source, n.s.getEndLocation())));
      if (w.shadow != null) {
        double GF = w.getGF(w.shadow.middle());
        if (Math.abs(GF) < 0.99) {
          GFs.add(GF);
        }
        /*for (int i = 0; i < maxPerWave - 1; i++) {
          double r = Math.random();
          GF = w.getGF(r * (w.shadow.start + (1 - r) * w.shadow.end));
          if (Math.abs(GF) < 0.99) {
            GFs.add(GF);
          }
        }*/
      }
      w.trackPreciseShadows = false;
    }
    return GFs;
  }

  public List<Double> getDangersAfterNewShadows(List<Double> GFs, Wave w) {
    List<WaveDangers> waveDangers = surfer.currentPlan.stream()
        .filter(n -> surfer.planVisitRanges.ranges.containsKey(n.surfWave))
        .map(WaveDangers::new)
        .collect(Collectors.toList());
    List<Double> dangers = new ArrayList<>();
    for (double GF : GFs) {
      Bullet bullet = new Bullet(w, w.getBearing(GF));
      dangers.add(Math.pow(1e-6 + waveDangers.stream().map(wd -> wd.getMinDanger(bullet))
          .mapToDouble(Double::doubleValue).sum(), waveDangers.size()));
    }
    return dangers;
  }

  public class WaveDangers {
    private final MovementWave w;
    private final double baseDanger;
    private final double[] unshadowedDangers;
    private final int reachableStart;
    private final int reachableEnd;
    private final int visitStart;
    private final int visitEnd;
    private final int widthBins;
    private final int depth;

    public WaveDangers(PathSurfer.Node n) {
      GFBins bins = Wave.BINS;
      w = n.surfWave;
      depth = n.depth;
      double width = Geom.maxBotAngle(n.s.getEndLocation().distance(w.source)) /
          ((w.maePrecise[0] + w.maePrecise[1]) / 2);
      widthBins = (int)Math.ceil(width / bins.binWidth);
      unshadowedDangers = surfer.dangerEstimator.getDangersWithoutShadows(w);
      Range reachable = surfer.planVisitRanges.ranges.get(w);
      reachableStart = bins.getBin(reachable.start);
      reachableEnd = bins.getBin(reachable.end);
      if (n.approximateWaveLoc == null) {
        if (!n.s.visitOffsetRanges.containsKey(w)) {
          // TODO: shouldn't this case never happen?
          visitStart = visitEnd = -1;
        } else {
          Range planRange = w.getGFRange(n.s.visitOffsetRanges.get(w));
          visitStart = bins.getBin(planRange.start);
          visitEnd = bins.getBin(planRange.end);
        }
      } else {
        visitStart = bins.getBin(n.approximateWaveLoc.startGF);
        visitEnd = bins.getBin(n.approximateWaveLoc.startGF);
      }
      baseDanger = getMinDanger(w.shadows);
    }

    public double getMinDanger(Bullet bullet) {
      double[] shadows = w.shadows.clone();
      if (w.addBulletShadows(bullet, shadows)) {
        return getMinDanger(shadows);
      }
      return baseDanger;
    }

    public double getMinDanger(double[] shadows) {
      double visitDanger = 0;
      double minDanger = (reachableEnd < reachableStart + widthBins ? 0 : Double.POSITIVE_INFINITY);
      double currentDanger = 0;
      for (int i = reachableStart; i <= reachableEnd; i++) {
        double binDanger = unshadowedDangers[i] * (1 - shadows[i]);
        currentDanger += binDanger;
        if (i >= visitStart && i <= visitEnd) {
          visitDanger += binDanger;
        }
        if (i >= reachableStart + widthBins) {
          if (currentDanger < minDanger) {
            minDanger = currentDanger;
          }
          currentDanger -= unshadowedDangers[i - widthBins] * (1 - shadows[i - widthBins]);
        }
      }
      // use a combination of minimum reachable danger and danger for current move plan
      double visitWeight = depth == 3 ? 0 : 1 / depth;
      return w.power * (visitWeight * visitDanger + (1 - visitWeight) * minDanger);
    }
  }
}
