package kc.mega.shield;

import kc.mega.game.BotState;
import kc.mega.game.GameState;
import kc.mega.move.wave.MovementWave;
import kc.mega.move.wave.MovementWaves;
import kc.mega.utils.Geom;
import kc.mega.utils.Range;
import kc.mega.wave.Bullet;
import kc.mega.wave.Wave;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.HitByBulletEvent;
import robocode.util.Utils;


/** https://robowiki.net/wiki/Bullet_Shielding -- shoots enemy bullets out of the air. */
public class Shielder {
  private static final int POWERS = 10;  // how many bullet powers to consider
  private static final double WIGGLE_SIZE = 0.1;  // size of wiggle needed so we can shoot directly
                                                  // incoming bullets from a slight angle

  private final AdvancedRobot bot;
  private final GameState gs;
  private final MovementWaves movementWaves;
  private final List<Plan> plans;
  private final List<BulletPredictor> bulletPredictors;

  private int wiggleDirection = 1;
  private int lastOrbitDirection = 1;
  private boolean enemyHasMoved = false;

  public Shielder(AdvancedRobot bot, GameState gs, MovementWaves movementWaves) {
    this.bot = bot;
    this.gs = gs;
    this.movementWaves = movementWaves;
    plans = new ArrayList<>();
    bulletPredictors = Arrays.asList(
        new ApproxAbsBearingPredictor(),
        new AbsBearingPredictor(),
        new PrevAbsBearingPredictor(),
        new NoAdjustPrevAbsBearingPredictor(),
        new PrevAbsBearingWithDirectionalOffsetPredictor());
  }

  public void onRoundStart() {
    if (bot.getRoundNum() > 0) {
      System.out.println("Bullet shielding using " + getBestPredictor().getClass().getSimpleName());
    }
    plans.clear();
  }

  public void shield() {
    BotState myState = gs.myState, enemyState = gs.enemyState;
    if (enemyState.velocity != 0) {
      enemyHasMoved = true;
    }

    BotState prevState = gs.getMyState(-2);
    if (prevState.velocity != 0) {
      lastOrbitDirection = Geom.orbitDirection(gs.getEnemyState(-2).absoluteBearing(prevState),
          prevState.heading, (int)Math.signum(prevState.velocity));
    }

    if (gs.enemyFiredLastTick || gs.gameTime == 20) {
      MovementWave w = (MovementWave)new MovementWave.Builder()
          .isVirtual(false)
          .myHistory(gs.enemyHistory.subList(1, gs.enemyHistory.size()))
          .enemyHistory(gs.myHistory.subList(1, gs.myHistory.size()))
          .power(gs.lastEnemyBulletPower)
          .hasBullet(gs.enemyFiredLastTick)
          .lastOrbitDirection(lastOrbitDirection)
          .build();
      if (gs.gameTime != 20) {  // warmup on tick 20 helps with not skipping a turn the first wave
        movementWaves.add(w);
      }

      wiggleDirection *= -1;
      List<Plan> options = new ArrayList<>();
      long fireTime = gs.gameTime + Math.max(1,
          (long)Math.ceil(bot.getGunHeat() / bot.getGunCoolingRate()));
      double predictedBulletHeading = getBestPredictor().predictBulletHeading(w);
      // generate candidate movement/firing plans
      for (boolean wiggle : new boolean[] {true, false}) {
        for (double bulletPower : getPowers(myState, w, fireTime, wiggle, predictedBulletHeading)) {
          options.add(new Plan(w, bulletPower, fireTime, wiggle, predictedBulletHeading));
        }
      }
      // select the best plan and add it
      if (options.size() > 0 && gs.gameTime != 20) {
        plans.add(options.stream().max((p1, p2) -> (int)Math.signum(p1.score - p2.score)).get());
      }
    }
    for (MovementWave w : movementWaves.update(myState)) {
      gs.enemyHitRateTracker.logShotPassed(w.power);
    }

    if (plans.isEmpty()) {
      bot.setAhead(0);
      bot.setTurnRightRadians(Utils.normalRelativeAngle(parallelHeading(
          myState.absoluteBearing(enemyState)) - bot.getHeadingRadians()));
      bot.setTurnGunRightRadians(myState.offset(enemyState, bot.getGunHeadingRadians()));
    } else if (plans.get(0).execute(gs.gameTime)) {
      plans.remove(0);
    }
  }

  private double parallelHeading(double absBearing) {
    return Math.sin(bot.getHeadingRadians() - absBearing) > 0 ?
        absBearing + Math.PI/2 : absBearing - Math.PI/2;
  }

  public List<Double> getPowers(BotState myState, MovementWave targetWave,
      long fireTime, boolean wiggle, double predictedBulletHeading) {
    List<Double> powers = new ArrayList<>();
    if (!enemyHasMoved) {
      powers.add(0.1);
      return powers;
    }
    for (int i = 0; i < POWERS; i++) {
      powers.add(0.1 * Math.pow(Math.pow(Math.max(0.2, targetWave.power) * 10, 1.0 / POWERS), i));
    }

    // compute additional powers that precisely intersect the predicted incoming bullet
    double heading = parallelHeading(Geom.absoluteBearingPrecise(
        myState.location, targetWave.source));
    Point2D.Double fireLocation = wiggle ? Geom.projectPrecise(
        myState.location, heading, wiggleDirection * WIGGLE_SIZE) :  myState.location;
    Bullet enemyBullet = new Bullet(targetWave, predictedBulletHeading);
    Point2D.Double bulletPosition = enemyBullet.getMidpoint(fireTime);
    double bulletDist = bulletPosition.distance(fireLocation);
    double lastBulletDist = Double.POSITIVE_INFINITY;
    long time = fireTime;
    while (bulletDist < lastBulletDist) {
      lastBulletDist = bulletDist;
      time++;
      bulletPosition = enemyBullet.getMidpoint(time);
      bulletDist = bulletPosition.distance(fireLocation);
      double neededSpeed = bulletDist / (time - fireTime + 0.5);
      if (neededSpeed > 11 && neededSpeed < 19.7) {
        double power = (20 - neededSpeed) / 3;
        if (power < targetWave.power) {
          powers.add(power);
        }
      }
    }
    return powers;
  }

  private class Plan {
    private final long fireTime;
    private final double bulletPower;
    private final double fireAngle;
    private final double score;
    private final double heading;
    private final boolean wiggle;
    private final int direction;

    public Plan(MovementWave targetWave, double bulletPower, long fireTime, boolean wiggle,
        double predictedBulletHeading) {
      this.fireTime = fireTime;
      this.bulletPower = bulletPower;
      this.wiggle = wiggle;
      this.direction = wiggleDirection;

      heading = parallelHeading(Geom.absoluteBearingPrecise(
          gs.myState.location, targetWave.source));
      Point2D.Double fireLocation = wiggle ? Geom.projectPrecise(
          gs.myState.location, heading, direction * WIGGLE_SIZE) : gs.myState.location;
      Wave myWave = new Wave.Builder()
          .trackPreciseShadows(true)
          .myHistory(Collections.singletonList(new BotState(
              fireLocation, 0, 0, 0, 0, 0, 0, fireTime)))
          .enemyHistory(gs.enemyFuture.subList(
              Math.min(gs.enemyFuture.size() - 2, (int)(fireTime - gs.gameTime)),
              gs.enemyFuture.size()))
          .power(bulletPower)
          .build();
      Bullet enemyBullet = new Bullet(targetWave, predictedBulletHeading);
      myWave.addBulletShadows(enemyBullet);
      Range shadow = myWave.shadow;
      double width = shadow == null ? 0 : shadow.width();
      fireAngle = myWave.absoluteBearing + (shadow == null ? 0 : shadow.middle());
      double targetPower = targetWave.power * gs.myState.energy /
          (gs.enemyState.energy + targetWave.power);
      double targetPowerConservative = 0.9 * targetWave.power *
          Math.max(0.1, gs.myState.energy - 1) /
          (gs.enemyState.energy + targetWave.power - 1);
      // heuristic score: make a large shadow, try not to move, and use a low bullet power
      score = (shadow == null ? 0 : 1)
          + (width > 1e-6 ? 0.2 : 0)
          + (width > 1e-5 ? 0.03 : 0)
          + (!wiggle && width > 1e-4 ? 0.1 : 0)
          + (bulletPower < targetPower ? 0.01 : 0)
          + (bulletPower < targetPowerConservative ? 0.01 : 0)
          + width
          - bulletPower * 1e-10;
    }

    public boolean execute(long gameTime) {
      bot.setTurnRightRadians(Utils.normalRelativeAngle(heading - bot.getHeadingRadians()));
      bot.setTurnGunRightRadians(Utils.normalRelativeAngle(fireAngle - bot.getGunHeadingRadians()));
      if (gameTime == fireTime - 1 && wiggle) {
        bot.setAhead(direction * WIGGLE_SIZE);
      } else if (gameTime == fireTime) {
        bot.setFire(bulletPower);
        if (wiggle) {
          bot.setAhead(-direction * WIGGLE_SIZE);
        }
      }
      return gameTime >= fireTime;
    }
  }

  private BulletPredictor getBestPredictor() {
    return bulletPredictors.stream().max((a1, a2) -> (int)Math.signum(a1.score - a2.score)).get();
  }

  // bots using standard head on targeting
  private class PrevAbsBearingPredictor extends BulletPredictor {
    @Override
    public double getHeadOnBearing(MovementWave w) {return w.prevAbsoluteBearing;}
    @Override
    public double getBaseScore() {return 5e-5;}
  }

  // bots not calling setAdjustGunForRobotTurn(true)
  private class NoAdjustPrevAbsBearingPredictor extends BulletPredictor {
    @Override
    public double getHeadOnBearing(MovementWave w) {
      return w.prevAbsoluteBearing + w.fireState.heading - w.myState.heading;
    }
    @Override
    public double getBaseScore() {return 4e-5;}
  }

  // bots approximately predicting their next location
  private class ApproxAbsBearingPredictor extends BulletPredictor {
    @Override
    public double getHeadOnBearing(MovementWave w) {
      return Geom.absoluteBearingPrecise(w.myState.getNextState(0).location, w.enemyState.location);
    }
    @Override
    public double getBaseScore() {return 3e-5;}
  }

  // bots precisely predicting their next location
  private class AbsBearingPredictor extends BulletPredictor {
    @Override
    public double getHeadOnBearing(MovementWave w) {return w.absoluteBearing;}
    @Override
    public double getBaseScore() {return 2e-5;}
  }

  // bots adding a fixed offset based on our orbit direction to the abs bearing
  private class PrevAbsBearingWithDirectionalOffsetPredictor extends PrevAbsBearingPredictor {
    @Override
    public double adjustOffset(double offset, MovementWave w) {return offset * w.orbitDirection;}
    @Override
    public double getBaseScore() {return 1e-5;}
  }

  private static abstract class BulletPredictor {
    public final List<Double> hitOffsets;
    public double mostLikelyOffset;
    public double score;

    public BulletPredictor() {
      hitOffsets = new ArrayList<>();
      setOffsetAndScore();
    }

    public abstract double getBaseScore();

    public abstract double getHeadOnBearing(MovementWave w);

    public double predictBulletHeading(MovementWave w) {
      return getHeadOnBearing(w) + adjustOffset(mostLikelyOffset, w);
    }

    public void logBullet(MovementWave w, double bulletHeading) {
      hitOffsets.add(adjustOffset(Utils.normalRelativeAngle(
          bulletHeading - getHeadOnBearing(w)), w));
      if (hitOffsets.size() > 500) {
        hitOffsets.remove(0);
      }
      setOffsetAndScore();
    }

    public double adjustOffset(double offset, MovementWave w) {
      return offset;
    }

    public double getOffsetScore(int offsetIndex, int historySize) {
      double offset = hitOffsets.get(offsetIndex);
      double offsetScore = (Math.abs(offset) < 0.01 ? 2 : 0) - Math.abs(offset) +
          offsetIndex * 1e-8;
      for (int j = 0; j < Math.min(hitOffsets.size(), historySize); j++) {
        if (offsetIndex != j && Math.abs(hitOffsets.get(j) - offset) < 1e-5) {
          offsetScore += 1;
        }
      }
      return offsetScore;
    }

    public void setOffsetAndScore() {
      double bestScore = 0;
      int bestIndex = 0;
      for (int i = 0; i < hitOffsets.size(); i++) {
        double offsetScore = getOffsetScore(i, 20);
        if (offsetScore > bestScore) {
          bestScore = offsetScore;
          bestIndex = i;
        }
      }
      mostLikelyOffset = hitOffsets.isEmpty() ? 0 : hitOffsets.get(bestIndex);
      score = getBaseScore() + (hitOffsets.isEmpty() ? 0 : getOffsetScore(bestIndex, 1000));
    }
  }

  public void onHitByBullet(HitByBulletEvent e) {
    MovementWave hitWave = movementWaves.getHitWave(e.getPower());
    if (hitWave != null && hitWave.fireTime < bot.getTime() - 1) {
      logBullet(hitWave, e.getHeadingRadians());
    }
  }

  public void onBulletHitBullet(BulletHitBulletEvent e) {
    MovementWave hitWave = movementWaves.getBulletHitBulletWave(
        new Point2D.Double(e.getHitBullet().getX(), e.getHitBullet().getY()),
        e.getHitBullet().getPower(), bot.getTime());
    if (hitWave != null) {
      logBullet(hitWave, e.getHitBullet().getHeadingRadians());
    }
  }

  public void logBullet(MovementWave w, double bulletHeading) {
    for (BulletPredictor bulletPredictor : bulletPredictors) {
      bulletPredictor.logBullet(w, bulletHeading);
    }
  }
}
