package kc.mega.aim;

import java.awt.geom.Rectangle2D;

import kc.mega.game.BattleField;
import kc.mega.game.GameState;
import kc.mega.game.HitRateTracker;
import kc.mega.game.Physics;
import kc.mega.shared.Strategy;
import kc.mega.utils.MathUtils;
import kc.mega.utils.Painter;


/** Selects bullet powers by trying to maximize the expected score from the battle. */
public class BulletPowerSelector {
  // Bullet powers that exploit a bug in BasicSurfer (which many rumble bots are based off)
  private static final double[] CANDIDATE_POWERS_ABS = new double[] {
      Math.nextAfter(2.45, -1), 1.95, 1.45, 0.95, 0.65, 0.45, 0.15};
  private static final double[] CANDIDATE_POWERS = new double[] {
      2.99, 2.75, 2.49, 2.3, 2.2, 2.1, 1.99, 1.9, 1.8, 1.7, 1.6, 1.49, 1.4, 1.3, 1.2, 1.1, 0.99,
      0.95, 0.9, 0.85, 0.8, 0.75, 0.7, 0.65, 0.6, 0.55, 0.49, 0.45, 0.4, 0.35, 0.3, 0.25, 0.2,
      0.175, 0.15, 0.125, 0.1};
  private static final double FULL_POWER_DISTANCE = 100;  // fire full power at close range
  private static final double MIN_ENERGY = 0.05;  // conserve a tiny bit of energy
  // robocode scoring parameters (https://robowiki.net/wiki/Robocode/Scoring)
  private static final double POINTS_FOR_WIN = 60;
  private static final double BULLET_DAMAGE_BONUS = 0.2;

  private final HitRateTracker myHitRateTracker;
  private final HitRateTracker enemyHitRateTracker;
  private final double myEnergy;
  private final double enemyEnergy;
  private final BulletPower enemyPower;

  public BulletPowerSelector(GameState gs) {
    myHitRateTracker = gs.myHitRateTracker;
    enemyHitRateTracker = gs.enemyHitRateTracker;
    myEnergy = gs.myState.energy;
    enemyEnergy = gs.enemyState.energy;
    enemyPower = new BulletPower(Math.min(enemyEnergy, gs.lastEnemyBulletPower < 0 ?
        gs.enemyFirstShotBulletPower : gs.lastEnemyBulletPower), false);
  }

  public static double bestBulletPower(GameState gs, Strategy strategy) {
    BulletPowerSelector selector = new BulletPowerSelector(gs);
    boolean fullPower = gs.myState.distance(gs.enemyState) < FULL_POWER_DISTANCE ||
        strategy.antiRamCount > 0;
    double myEnergy = gs.myState.energy;
    double enemyEnergy = gs.enemyState.energy;

    double killEnergy = enemyEnergy + (
        strategy.tryToDisable && !strategy.antiBasicSurfer ? -0.011 : 0);
    double minimumKillPower = killEnergy/ 4;
    if (killEnergy > 4) {
      minimumKillPower = ((killEnergy + 2) / 6);
    }

    double bestPower = 0;
    if (fullPower) {
      bestPower = Math.nextAfter(2.95, -1);
    } else {
      double bestScore = Double.NEGATIVE_INFINITY;
      for (double power : (strategy.antiBasicSurfer ? CANDIDATE_POWERS_ABS : CANDIDATE_POWERS)) {
        double score = selector.scorePower(power, gs.roundNum <= 4);
        if (score > bestScore) {
          bestScore = score;
          bestPower = power;
        }
      }
    }

    if (!strategy.antiBasicSurfer) {
      bestPower = MathUtils.clip(bestPower, 0.1, minimumKillPower);
      bestPower = Math.min(bestPower, myEnergy - MIN_ENERGY);
      if (fullPower) {
        bestPower = Math.max(bestPower, 0.1);
      } else if (myEnergy < 5 && myEnergy > enemyEnergy) {
        // stop shooting if it gives up the energy advantage and we have low energy
        bestPower = Math.min(myEnergy - enemyEnergy - 0.11, bestPower);
      }
    } else {
      // discrete version of the above logic so we always use an ABS power
      for (int i = CANDIDATE_POWERS_ABS.length - 1; i >= 0; i--) {
        if (CANDIDATE_POWERS_ABS[i] > minimumKillPower) {
          bestPower = Math.min(CANDIDATE_POWERS_ABS[i], bestPower);
          break;
        }
      }
      for (double power : CANDIDATE_POWERS_ABS) {
        if (bestPower > myEnergy && power < myEnergy) {
          bestPower = power;
          break;
        }
      }
      if (bestPower > myEnergy ||
          (myEnergy < 5 && myEnergy > enemyEnergy && myEnergy - bestPower < enemyEnergy + 0.11)) {
        bestPower = -1;
      }
    }

    if (Painter.active && bestPower > 0) {
      // paint bar showing the estimated win probability
      double height = 25 * selector.estimateWinProb(bestPower);
      Painter.THIS_TICK.addShape(Painter.TRANSLUCENT_GREEN, new Rectangle2D.Double(
          10, 0, 10, height), true);
      Painter.THIS_TICK.addShape(Painter.TRANSLUCENT_RED, new Rectangle2D.Double(
          10, height, 10, 25 - height), true);
    }

    return bestPower;
  }

  private double scorePower(double power, boolean ratio) {
    BulletPower myPower = new BulletPower(power, true);
    // linear approximations for expected damage and expected ticks left in the round
    double ticksLeft = estimateTicksLeft(myPower, myPower.hitRate, enemyPower.hitRate);
    double myDamage = myHitRateTracker.getDamageThisRound() +
        myPower.damage * myPower.hitRate * ticksLeft;
    double enemyDamage = enemyHitRateTracker.getDamageThisRound() +
        enemyPower.damage * enemyPower.hitRate * ticksLeft;
    // estimate the probability we win the round and compute expected score
    double winProb = estimateWinProb(power);
    double myScore = myDamage * (1 + BULLET_DAMAGE_BONUS * winProb) + POINTS_FOR_WIN * winProb;
    double enemyScore = enemyDamage * (1 + BULLET_DAMAGE_BONUS * (1 - winProb)) +
        POINTS_FOR_WIN * (1 - winProb);
    if (ratio) {  // roborumble uses avg percent score (ratio), but early on we use difference
      double myTotalScore = myHitRateTracker.getApproximateScore() + myScore;
      return myTotalScore / (myTotalScore + enemyHitRateTracker.getApproximateScore() + enemyScore);
    } else {
      return myScore - enemyScore;
    }
  }

  private double estimateWinProb(double power) {
    BulletPower myPower = new BulletPower(power, true);
    // using Lagrange multipliers to solve for hit rates that win and minimize squared error to the
    // average hit rates
    double a = myEnergy * myPower.damage + enemyEnergy * myPower.gain;
    double b = enemyEnergy * enemyPower.damage + myEnergy * enemyPower.gain;
    double lambda = (enemyEnergy * myPower.loss) - (myEnergy * enemyPower.loss) -
        (myPower.hitRate * a) + (enemyPower.hitRate * b);
    lambda /= a * a + b * b;
    double myNeededHR = lambda * a + myPower.hitRate;
    double enemyNeededHR = -lambda * b + enemyPower.hitRate;
    if (myNeededHR <= 0 || enemyNeededHR >= 1) {
      return 1;
    } else if (myNeededHR >= 1 || enemyNeededHR <= 0) {
      return 0;
    } else {
      double ticksLeft = estimateTicksLeft(myPower, myNeededHR, enemyNeededHR);
      return Math.sqrt((1 - myPower.probAboveHR(myNeededHR, ticksLeft)) *
          enemyPower.probAboveHR(enemyNeededHR, ticksLeft));
    }
  }

  private double estimateTicksLeft(BulletPower myPower, double myHitRate, double enemyHitRate) {
    double myCombinedLoss = Math.max(
        enemyPower.damage * enemyHitRate - myPower.gain * myHitRate + myPower.loss, 0.0001);
    double enemyCombinedLoss = Math.max(
        myPower.damage * myHitRate - enemyPower.gain * enemyHitRate + enemyPower.loss, 0.0001);
    return Math.min(myEnergy / myCombinedLoss, enemyEnergy / enemyCombinedLoss);
  }

  private class BulletPower {
    public final int cooldown;
    public final double damage;
    public final double gain;
    public final double loss;
    public final double hitRate;

    public BulletPower(double power, boolean isMine) {
      cooldown = (int)(Physics.gunHeat(power) / BattleField.INSTANCE.getGunCoolingRate());
      damage = Physics.bulletDamage(power) / cooldown;
      gain = 3 * power / cooldown;
      loss = power / cooldown;
      // assume (1) the enemy has a <15% hit rate and (2) we are more accurate than our enemy; this
      // can be useful early in the battle when hit rate estimates are poor
      double enemyHitRate = Math.min(0.15, enemyHitRateTracker.estimateHitRate(power));
      if (isMine) {
        hitRate = Math.max(enemyHitRate, myHitRateTracker.estimateHitRate(power));
      } else {
        hitRate = enemyHitRate;
      }
    }

    private double probAboveHR(double targetHR, double ticks) {
      double shots = ticks / cooldown;
      double expectedHits = hitRate * shots;
      double targetHits = targetHR * shots;
      double variance = hitRate * (1 - hitRate) * shots;
      // approximate binomial cdf with normal cdf
      return phi((targetHits - expectedHits) / Math.sqrt(variance));
    }
  }

  // https://en.wikipedia.org/wiki/Error_function#Approximation_with_elementary_functions
  private static double erf(double z) {
    double t = 1.0 / (1.0 + 0.5 * Math.abs(z));
    double ans = 1 - t * Math.exp(-z * z - 1.26551223 +
        t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 +
            t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * (0.17087277))))))))));
    return z > 0 ? ans : -ans;
  }

  private static double phi(double x) {
    return (1.0 + erf(x / Math.sqrt(2.0))) / 2.0;
  }
}
