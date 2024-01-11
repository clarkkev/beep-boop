package kc.mega.aim.models;

import kc.mega.model.WaveKNN;
import kc.mega.utils.Range;

/** Two KNN models - one trained to hit wave surfers and one trained for other bots. */
public class AimModels {
  public static KNNAimModel getMainModel() {
    return new KNNAimModel("Main", new WaveKNN.Builder<Range>()
        .features(new String[] {"power", "virtuality", "bft", "accel", "vel", "vel=8", "advDir", "dirChangeTimer", "decelTimer", "distanceLast20", "mirrorOffset", "maeWallAhead", "maeWallReverse", "stickWallAhead", "stickWallReverse", "stickWallAhead2", "stickWallReverse2", "stickWallAhead=0", "stickWallReverse=0", "shotsFired"})
        .params(new double[][] {
          {0.6451, 0.0221, 0.3791},
          {0.6586, 0.5277, 0.7128},
          {3.8637, 0.0721, 0.5527},
          {6.1880, 0.0179, 0.1242},
          {1.5331, 0.3616, 0.4985},
          {0.7015, 0.3663, 1.3652},
          {0.9362, 1.1348, 1.6805},
          {0.7125, 0.0178, 0.3897},
          {0.5285, 0.0228, 0.2664},
          {0.4369, 0.5886, 1.1668},
          {0.3591, 0.3235, 1.2803},
          {2.3051, 0.2179, 1.4084},
          {1.0039, 0.0585, 1.7278},
          {1.9669, 0.3157, 1.6041},
          {0.5608, 0.0414, 0.5860},
          {1.4668, 0.1155, 0.2835},
          {0.0646, 0.3137, 0.0688},
          {0.0837, 0.0183, 1.0184},
          {0.2553, 0.1974, 1.1975},
          {0.6133, 0.0291, 0.4828},
        })
        .distanceScale(-0.6551)
        .neighborhoodSizeDivider(5.0)
        .maxNeighbors(200)
        .maxTreeSize(50000).build());
  }

  public static KNNAimModel getAntiSurferModel() {
    return new KNNAimModel("AntiSurfer", new WaveKNN.Builder<Range>()
        .features(new String[] {"virtuality", "bft", "accel", "vel", "vel=8", "advDir", "dirChangeTimer", "decelTimer", "distanceLast10", "mirrorOffset", "maeWallAhead", "maeWallReverse", "stickWallAhead", "stickWallReverse", "stickWallAhead2", "didHit", "didCollide"})
        .params(new double[][] {
          {0.9153, 0.0360, 0.6818},
          {3.7350, 0.0171, 1.5420},
          {5.2902, 0.3806, 0.2463},
          {1.3547, 0.4297, 0.4006},
          {0.3843, 0.1186, 0.8882},
          {0.6976, 0.6359, 1.6764},
          {0.7867, 0.0168, 0.1116},
          {1.4397, 0.0128, 0.2504},
          {0.0669, 0.5042, 0.7368},
          {0.0610, 0.1139, 1.0781},
          {2.3476, 0.3259, 1.3857},
          {1.3820, 0.3746, 1.3689},
          {0.3754, 0.7164, 0.5154},
          {0.4717, 0.0613, 1.2691},
          {0.8256, 0.0157, 0.3642},
          {2.1621, 0.1432, 1.1444},
          {0.8559, 0.8645, 1.8590}})
        .distanceScale(-0.8611)
        .neighborhoodSizeDivider(5.0)
        .maxNeighbors(100)
        .maxTreeSize(50000).build());
  }
}
