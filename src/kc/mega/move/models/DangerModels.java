package kc.mega.move.models;

import kc.mega.model.WaveKNN;
import kc.mega.wave.GFBins;

/** Collection of danger models; many are learned offline with gradient descent. */
public class DangerModels {
  public static DangerModel hot(GFBins bins) {
    return new SimpleDangerModels.HOTModel(bins);
  }

  public static DangerModel linear(GFBins bins) {
    return new SimpleDangerModels.LinearTargetingModel("Linear", bins, false, false);
  }

  public static DangerModel linearWithWalls(GFBins bins) {
    return new SimpleDangerModels.LinearTargetingModel("LinearWithWalls", bins, false, true);
  }

  public static DangerModel circular(GFBins bins) {
    return new SimpleDangerModels.LinearTargetingModel("Circular", bins, true, true);
  }

  public static DangerModel avgLinear(GFBins bins) {
    return new SimpleDangerModels.LinearTargetingModel(
        "AvgLinear", bins, false, false, true, false);
  }

  public static DangerModel nanoLinearFixedSpeed(GFBins bins) {
    return new SimpleDangerModels.NanoLinearModel(bins, true);
  }

  public static DangerModel currentGF(GFBins bins) {
    return new SimpleDangerModels.CurrentGFModel(bins);
  }

  public static DangerModel apmFlattener(GFBins bins) {
    return new APMFlattener(bins);
  }

  public static DangerModel simple(GFBins bins) {
    return new KNNDangerModel("Simple", bins, new WaveKNN.Builder<Double>()
        .features(new String[] {"bft", "vel", "latVel", "advDir", "maeWallAhead"})
        .params(new double[][] {{0.5}, {2}, {6}, {1}, {2}})
        .distanceScale(-2)
        .neighborhoodSizeDivider(2)
        .maxNeighbors(50)
        .maxTreeSize(3000)
        .build());
  }

  public static DangerModel simple2(GFBins bins) {
    return new KNNDangerModel("Simple2", bins, new WaveKNN.Builder<Double>()
        .features(new String[] {"bft", "accel", "vel", "advDir", "maeWallAhead", "stickWallAhead"})
        .params(new double[][] {{1}, {1}, {2}, {1}, {1}, {1}})
        .distanceScale(-0.5)
        .neighborhoodSizeDivider(2)
        .maxNeighbors(50)
        .maxTreeSize(3000)
        .build());
  }

  public static DangerModel thorn(GFBins bins) {
    return new KNNDangerModel("Thorn", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "accel", "vel", "orbitalWallAhead", "orbitalWallReverse", "maeWallAhead", "maeWallReverse", "shotsFired"})
      .params(new double[][] {{10.3438}, {1.6888}, {2.1116}, {1.7250}, {0.9364}, {1.4386}, {2.1662}, {3.1504}})
      .distanceScale(-1.6325)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  public static DangerModel sedan(GFBins bins) {
    return new KNNDangerModel("Sedan", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "accel", "latVel", "maeWallAhead", "shotsFired"})
      .params(new double[][] {{7.8667}, {0.7579}, {1.3128}, {1.1977}, {3.9877}})
      .distanceScale(-2.4324)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  public static DangerModel druss(GFBins bins) {
    return new KNNDangerModel("Druss", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "accel", "vel", "latVel", "advDir", "vChangeTimer", "decelTimer", "distanceLast10", "maeWallAhead", "maeWallReverse", "stickWallAhead", "stickWallReverse", "shotsFired"})
      .params(new double[][] {{7.4265}, {3.2607}, {0.3331}, {3.0054}, {4.5670}, {0.6459}, {2.3745}, {2.8849}, {4.1025}, {1.2191}, {0.1208}, {0.3755}, {0.5554}})
      .distanceScale(-0.2199)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  public static DangerModel diamond(GFBins bins) {
    return new KNNDangerModel("Diamond", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "accel", "latVel", "advDir", "vChangeTimer", "orbitalWallAhead", "orbitalWallReverse", "maeWallAhead", "maeWallReverse", "shotsFired"})
      .params(new double[][] {{10.4572}, {1.5877}, {0.2986}, {6.1020}, {3.0055}, {2.1124}, {2.6054}, {2.5548}, {2.0901}, {0.3814}})
      .distanceScale(-0.4383)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  public static DangerModel komarious(GFBins bins) {
    return new KNNDangerModel("Komarious", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "accel", "latVel", "advDir", "vChangeTimer", "orbitalWallAhead", "orbitalWallReverse", "stickWallAhead", "stickWallReverse", "shotsFired"})
      .params(new double[][] {{2.2555}, {1.6461}, {2.4056}, {2.4558}, {2.4851}, {2.9594}, {1.4271}, {1.6963}, {1.8807}, {4.0817}})
      .distanceScale(-1.0133)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  public static DangerModel splinter(GFBins bins) {
    return new KNNDangerModel("Splinter", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "latVel", "maeWallAhead"})
      .params(new double[][] {{5.5207}, {2.1004}, {0.9032}})
      .distanceScale(-2.7087)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  public static DangerModel waveShark(GFBins bins) {
    return new KNNDangerModel("WaveShark", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "accel", "latVel", "advDir", "dirChangeTimer", "decelTimer", "distanceLast20", "orbitalWallAhead", "orbitalWallReverse", "maeWallAhead", "maeWallReverse"})
      .params(new double[][] {{4.7128}, {2.9091}, {3.6226}, {1.7127}, {1.6968}, {3.5238}, {0.9638}, {1.0648}, {0.7448}, {2.4531}, {1.1200}})
      .distanceScale(-0.1762)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  public static DangerModel microAspid(GFBins bins) {
    return new KNNDangerModel("MicroAspid", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"bft", "accel", "latVel", "vChangeTimer", "decelTimer", "orbitalWallAhead", "orbitalWallReverse", "stickWallAhead", "stickWallReverse"})
      .params(new double[][] {{7.0838}, {0.0026}, {5.4960}, {1.7377}, {1.1942}, {0.4861}, {0.4431}, {2.5026}, {1.7946}})
      .distanceScale(-0.1865)
      .neighborhoodSizeDivider(2)
      .maxNeighbors(50)
      .maxTreeSize(3000)
      .build());
  }

  // https://robowiki.net/wiki/Flattener trained against diamond and druss
  public static DangerModel flattener1(GFBins bins) {
    return new KNNDangerModel("Flattener1", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"virtuality", "bft", "accel", "vel", "maeWallAhead", "shotsFired"})
      .params(new double[][] {{2.8498}, {3.2063}, {2.8554}, {4.0240}, {6.0501}, {3.9666}})
      .distanceScale(-0.0956)
      .neighborhoodSizeDivider(5)
      .maxNeighbors(50)
      .maxTreeSize(5000)
      .build());
  }

  // https://robowiki.net/wiki/Flattener trained against waveserpent, cassiusclay, and gressuffard
  public static DangerModel flattener2(GFBins bins) {
    return new KNNDangerModel("Flattener2", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"virtuality", "bft", "accel", "vel", "advDir", "maeWallAhead", "maeWallReverse", "stickWallAhead", "shotsFired"})
      .params(new double[][] {{2.7282}, {2.8465}, {1.3839}, {5.0453}, {3.4051}, {2.1750}, {1.2043}, {1.7921}, {5.7242}})
      .distanceScale(-0.1651)
      .neighborhoodSizeDivider(5)
      .maxNeighbors(50)
      .maxTreeSize(5000)
      .build());
  }

  // https://robowiki.net/wiki/Flattener trained against tomcat and gilgalad
  public static DangerModel flattener3(GFBins bins) {
    return new KNNDangerModel("Flattener3", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"virtuality", "bft", "accel", "vel", "advDir", "dirChangeTimer", "maeWallAhead", "maeWallReverse", "stickWallAhead", "stickWallReverse", "shotsFired"})
      .params(new double[][] {{2.1865}, {4.9589}, {0.8323}, {1.0450}, {2.2741}, {0.6626}, {0.7798}, {0.7008}, {1.3206}, {1.2121}, {2.0052}})
      .distanceScale(-0.6058)
      .neighborhoodSizeDivider(5)
      .maxNeighbors(50)
      .maxTreeSize(5000)
      .build());
  }

   // https://robowiki.net/wiki/Flattener trained against beepboop 0.1, komarious, and cunobelin
  public static DangerModel flattener4(GFBins bins) {
    return new KNNDangerModel("Flattener4", bins, new WaveKNN.Builder<Double>()
      .features(new String[] {"virtuality", "bft", "accel", "vel", "advDir", "dirChangeTimer", "maeWallAhead", "maeWallReverse", "shotsFired"})
      .params(new double[][] {{2.1167}, {2.3384}, {0.5146}, {1.5453}, {2.2296}, {1.0241}, {2.3923}, {1.1432}, {4.4724}})
      .distanceScale(-0.5052)
      .neighborhoodSizeDivider(5)
      .maxNeighbors(50)
      .maxTreeSize(5000)
      .build());
  }
}
