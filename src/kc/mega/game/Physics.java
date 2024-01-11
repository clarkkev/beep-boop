package kc.mega.game;

import kc.mega.utils.MathUtils;

/** Utilities related to https://robowiki.net/wiki/Robocode/Game_Physics. */
public class Physics {
  public static double nextVelocity(double v, int d) {
    if (d == 0) {
      return v - Math.signum(v) * Math.min(decel(Math.abs(v)), Math.abs(v));
    }
    return MathUtils.clip(v + d * (Math.signum(v) * d < 0 ? decel(Math.abs(v)) : 1), -8, 8);
  }

  public static double decel(double speed) {
    return MathUtils.clip(1 + speed / 2, 1, 2);
  }

  public static double maxTurn(double v) {
    return Math.PI / 18 - Math.abs(v) * Math.PI / 240;
  }

  public static double turnIncrement(double t, double v) {
    double max = maxTurn(v);
    return MathUtils.clip(t, -max, max);
  }

  public static double bulletSpeed(double power) {
    return 20 - (3 * MathUtils.clip(power, 0.1, 3));
  }

  public static double bulletDamage(double power) {
    return 4 * power + Math.max(2 * (power - 1), 0);
  }

  public static double bulletDamage(double power, double enemyEnergy) {
    return Math.min(enemyEnergy, bulletDamage(power));
  }

  public static double gunHeat(double power) {
    return 1 + power / 5.0;
  }
}
