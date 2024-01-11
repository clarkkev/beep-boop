package kc.mega.wave;

import kc.mega.game.BattleField;
import kc.mega.game.BotState;
import kc.mega.utils.Geom;

import java.awt.geom.Point2D;
import java.util.List;

import jk.math.FastTrig;

/** Associates a wave with various features about the targeted bot. */
public class WaveWithFeatures extends Wave {
  public final BotState fireState;  // aimer's state for the next tick (when they fire)
  public final BotState myState;  // aimer's state for the current tick
  public final BotState enemyState;  // targeted bot's state for the current tick
  public final BotState prevEnemyState;  // targeted bot's state for the previous tick

  public final double prevAbsoluteBearing;  // absolute bearing from the aimer's last position
  public final int moveDirection;  // is the bot driving forward or reversing
  public final double distance;  // distance between the aimer and the bot
  public final double accel; // how much the bot is accelerating or decelerating
  public final double velocity;  // the bot's velocity
  public final double avgVelocity;  // exponential moving average of the bot's velocity
  public final double relativeHeading;  // diff betweeen the bot's heading and absolute bearing
  public final int vChangeTimer;  // ticks since the bot last decelerated
  public final int dirChangeTimer;  // ticks since the bot last changed their direction
  public final int decelTimer;  // ticks since the bot last changed their velocity
  public final double distanceLast10;  // distance the bot moved over the last 10 ticks
  public final double distanceLast20;  // distance the bot moved over the last 20 ticks
  public final double mirrorOffset;  // how much the bot is off from the aimer's mirrored position
  public final double stickWallAhead;  // how much the bot has to deviate from orbital heading to
                                       // avoid hitting a wall
  public final double stickWallReverse;  // stickWallAhead if the bot reversed their direction
  public final double stickWallAhead2;  // how much the bot has to deviate to avoid hitting a wall
  public final double stickWallReverse2;  // stickWallAhead2 if the bot reversed their direction
  public final double maeWallAhead;  // how much the wall restricts the bot's max escape angle
  public final double maeWallReverse;  // maeWallAhead if the bot reversed their direction
  public final double orbitalWallAhead;  // orbital degrees from the bot towards the wall
  public final double orbitalWallReverse;  // orbitalWallAhead if the bot reversed their direction
  public final int shotsFired;  // how many shots the aimer has fired in the battle
  public final double virtuality;  // how far from a real bullet wave this wave is
  public final double currentGF;  // the bot's current guessfactor
  public final String pattern;  // discrete history of bot movement for pattern matching

  public static class Builder extends Wave.Builder {
    public Waves<?> waves;
    public Builder waves(Waves<?> val) {waves = val; return this;}
    @Override
    public WaveWithFeatures build() {return new WaveWithFeatures(this);}
  }

  public WaveWithFeatures(Builder builder) {
    super(builder);

    fireState = builder.myHistory.get(0);
    Point2D.Double myNextLocation = fireState.location;
    // start at 1 because aiming was done the tick before the wave is created
    List<BotState> myHistory = builder.myHistory.subList(1, builder.myHistory.size());
    List<BotState> enemyHistory = builder.enemyHistory.subList(1, builder.enemyHistory.size());

    myState = myHistory.get(0);
    enemyState = enemyHistory.get(0);
    prevEnemyState = enemyHistory.size() == 1 ? enemyState : enemyHistory.get(1);

    Point2D.Double myLocation = myState.location;
    Point2D.Double enemyLocation = enemyState.location;

    int ticksSinceShot = hasBullet ? 0 : (int)(myState.gameTime - myState.lastFireTime);
    virtuality = hasBullet ? 0 : Math.min(
        (int)(Math.max(myState.gunHeat, 0) / BattleField.INSTANCE.getGunCoolingRate()),
        1 + ticksSinceShot);
    didHit = false;
    shotsFired = myState.shotsFired;
    currentGF = builder.waves == null ? 0 : builder.waves.getCurrentGF(enemyState);

    prevAbsoluteBearing = Geom.absoluteBearingPrecise(myLocation, enemyLocation);
    distance = myLocation.distance(enemyLocation);
    velocity = enemyState.velocity;
    relativeHeading = enemyState.heading - prevAbsoluteBearing;
    accel = Math.abs(enemyState.velocity - prevEnemyState.velocity) * (
        Math.abs(enemyState.velocity) < Math.abs(prevEnemyState.velocity) ? -1 : 1);

    int vChangeTimerr = enemyHistory.size();
    int dirChangeTimerr = enemyHistory.size();
    int decelTimerr = enemyHistory.size();
    int timersSet = 0;
    int moveDirectionn = (int)Math.signum(enemyState.velocity);
    double totalVelocity = 0;
    double totalWeight = 0;
    double weight = 1;
    String patternn = "";
    for (int i = 0; i < Math.min(71, enemyHistory.size()); i++) {
      BotState enemyState = enemyHistory.get(i);
      double vel = enemyState.velocity;
      totalVelocity += weight * vel;
      totalWeight += weight;
      weight *= 0.95;
      if (i - 1 < vChangeTimerr && i > 0 &&
          Math.abs(vel - enemyHistory.get(i - 1).velocity) > 0.01) {
        vChangeTimerr = i - 1;
        timersSet++;
      }
      if (vel != 0) {
        if (moveDirectionn == 0) {
          moveDirectionn = (int)Math.signum(vel);
        } else if(i - 1 < dirChangeTimerr && (int)Math.signum(vel) != moveDirectionn) {
          dirChangeTimerr = i - 1;
          timersSet++;
        }
      }
      if (i - 1 < decelTimerr && moveDirectionn != 0 && i > 0 &&
          enemyHistory.get(i - 1).velocity * moveDirectionn < vel * moveDirectionn) {
        decelTimerr = i - 1;
        timersSet++;
      }
      if (i < myHistory.size()) {
        patternn += getPMSymbol(myHistory.get(i), enemyState);
      }
      if (timersSet == 3 && i > 30) {
        break;
      }
    }
    if (moveDirectionn == 0) {
      moveDirectionn = 1;
    }
    moveDirection = moveDirectionn;
    vChangeTimer = vChangeTimerr;
    dirChangeTimer = dirChangeTimerr;
    decelTimer = decelTimerr;
    pattern = patternn;
    avgVelocity = totalVelocity / (totalWeight == 0 ? 1 : totalWeight);
    distanceLast10 = enemyLocation.distance(
        enemyHistory.get(Math.min(12, enemyHistory.size() - 1)).location);
    distanceLast20 = enemyLocation.distance(
        enemyHistory.get(Math.min(20, enemyHistory.size() - 1)).location);

    mirrorOffset = Geom.offset(
        myNextLocation, BattleField.INSTANCE.getCenter(), absoluteBearing) * orbitDirection;
    double maxEscapeAngle = Geom.simpleMaxEscapeAngle(speed);
    orbitalWallAhead = BattleField.INSTANCE.orbitalWallDistance(
        myState, enemyState, orbitDirection, maxEscapeAngle);
    orbitalWallReverse = BattleField.INSTANCE.orbitalWallDistance(
        myState, enemyState, -orbitDirection, maxEscapeAngle);
    double[] inFieldMaes = Geom.inFieldMaxEscapeAngles(
        myNextLocation, enemyLocation, speed);
    maeWallAhead = (orbitDirection < 0 ? inFieldMaes[0] : inFieldMaes[1]) / maxEscapeAngle;
    maeWallReverse = (orbitDirection > 0 ? inFieldMaes[0] : inFieldMaes[1]) / maxEscapeAngle;

    class StickWallFeaturizer {
      double get(double heading, int dir) {
        double smoothed = BattleField.INSTANCE.walkingStickSmooth(
            enemyState.location, heading, dir * moveDirection, dir * orbitDirection, 160, 18);
        return Math.abs(FastTrig.normalRelativeAngle(smoothed - heading));
      }
    }
    StickWallFeaturizer featurizer = new StickWallFeaturizer();
    double orbitalHeading = absoluteBearing + orbitDirection * moveDirection * Math.PI / 2;
    stickWallAhead = featurizer.get(orbitalHeading, 1);
    stickWallReverse = featurizer.get(orbitalHeading, -1);
    stickWallAhead2 = featurizer.get(enemyState.heading, 1);
    stickWallReverse2 = featurizer.get(enemyState.heading, -1);
  }

  public double[] toArray() {
    return new double[] {
        absoluteBearing,
        orbitDirection,
        moveDirection,
        distance,
        power,
        accel,
        Math.signum(accel),
        relativeHeading,
        velocity,
        vChangeTimer,
        dirChangeTimer,
        decelTimer,
        currentGF,
        hasBullet ? 1 : 0,
        didHit ? 1 : 0,
        didCollide ? 1 : 0,
        shotsFired,
        fireTime,
        distanceLast10,
        distanceLast20,
        mirrorOffset,
        orbitalWallAhead,
        orbitalWallReverse,
        maeWallAhead,
        maeWallReverse,
        stickWallAhead,
        stickWallReverse,
        stickWallAhead2,
        stickWallReverse2,
        virtuality,
        maePrecise[0],
        maePrecise[1],
        hitOffset,
        visitOffsetRange.start,
        visitOffsetRange.end
    };
  }

  public static String getPMSymbol(BotState myState, BotState enemyState) {
    return String.valueOf((char)(enemyState.velocity * Math.sin(
        enemyState.heading - myState.absoluteBearing(enemyState))));
  }
}
