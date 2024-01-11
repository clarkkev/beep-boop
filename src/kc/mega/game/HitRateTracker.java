package kc.mega.game;

import kc.mega.utils.Geom;
import kc.mega.utils.MathUtils;

/** Tracks the hit rate (with uncertainty estimates) of a bot. */
public class HitRateTracker {
  public static final int PRIOR_HITS = 1;
  public static final int PRIOR_SHOTS = 12;

  private int hits = 0;
  private int shots = 0;
  private int shotsPassed = 0;
  private double damage = 0;
  private double damageThisRound = 0;
  private double totalWeight = 0;
  private double approximateScore = 0;

  public void init() {
    damageThisRound = 0;
  }

  public void clear() {
    hits = 0;
    shots = 0;
    shotsPassed = 0;
    damage = 0;
    damageThisRound = 0;
    totalWeight = 0;
  }

  public String getHitRateStr() {
    return String.format("%d/%d = %.2f +- %.2f", hits, shotsPassed,
        100 * getHitRate(), 100 * hitRateCIWidth());
  }

  public double estimateHitRate(double power) {
    // simple prior for the beginning of the battle
    double hitRate = ((double)hits + PRIOR_HITS) / (shotsPassed + PRIOR_SHOTS);
    // hit rate correction taking into account max escape angle
    return MathUtils.clip(hitRate * (shotsPassed == 0 ? 1 : (totalWeight / shotsPassed) /
        Geom.simpleMaxEscapeAngle(Physics.bulletSpeed(power))), 0.001, 0.999);
  }

  public double getHitRate() {
    return hits / Math.max(1, (double)shotsPassed);
  }

  public double getMinHitRate() {
    return Math.max(getHitRate() - hitRateCIWidth(), 0);
  }

  public double getMaxHitRate() {
    return Math.min(getHitRate() + hitRateCIWidth(), 1);
  }

  private double hitRateCIWidth() {
    // heuristic confidence interval
    double z;
    if (shotsPassed < 50) {
      z = 1.282;
    } else {
      z = 0.842;
    }
    double p = getHitRate();
    return z * Math.sqrt(p * (1 - p) / Math.max(1, shotsPassed));
  }

  public boolean hitRateInBounds(double min, double max) {
    return min <= getMaxHitRate() && max >= getMinHitRate();
  }

  public double getDamage() {
    return damage;
  }

  public double getDamageThisRound() {
    return damageThisRound;
  }

  public int getHits() {
    return hits;
  }

  public int getShots() {
    return shots;
  }

  public double getApproximateScore() {
    return approximateScore;
  }

  public void logShot() {
    shots++;
  }

  public void logShotPassed(double power) {
    shotsPassed++;
    totalWeight += Geom.simpleMaxEscapeAngle(Physics.bulletSpeed(power));
  }

  public void logHit(double bulletDamage) {
    hits++;
    damage += bulletDamage;
    damageThisRound += bulletDamage;
  }

  public void onRoundEnd(boolean won) {
    // https://robowiki.net/wiki/Robocode/Scoring
    approximateScore += getDamageThisRound() * (won ? 1.2 : 1) + (won ? 60 : 0);
  }
}
