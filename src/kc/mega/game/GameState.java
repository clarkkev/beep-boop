package kc.mega.game;

import kc.mega.utils.Geom;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;


/** Tracks information about the current round. */
public class GameState {
  public static final int HISTORY_SIZE = 100;
  public static final int FUTURE_SIZE = 10;

  public final AdvancedRobot bot;
  public final HitRateTracker myHitRateTracker;
  public final HitRateTracker enemyHitRateTracker;

  public BotState myState;
  public BotState enemyState;
  public List<BotState> myHistory;
  public List<BotState> enemyHistory;
  public List<BotState> myFuture;
  public List<BotState> enemyFuture;

  public double lastEnemyBulletPower;
  public double enemyFirstShotBulletPower = 0.1;
  public boolean enemyFiredThisRound;
  public boolean enemyFiredLastTick;
  public boolean enemyIsAlive;
  public String enemyName;
  public int roundNum;
  public long gameTime;
  public long lastCollideTime;
  public long lastEnemyCollideTime;

  public GameState(AdvancedRobot bot) {
    this.bot = bot;
    myHitRateTracker = new HitRateTracker();
    enemyHitRateTracker = new HitRateTracker();
  }

  public void onRoundStart() {
    myState = enemyState = null;
    myHistory = new ArrayList<>();
    enemyHistory = new ArrayList<>();
    myFuture = new ArrayList<>();
    enemyFuture = new ArrayList<>();
    lastCollideTime = -1;
    lastEnemyCollideTime = -1;
    lastEnemyBulletPower = enemyFirstShotBulletPower;
    enemyFiredLastTick = false;
    enemyFiredThisRound = false;
    gameTime = 0;
    myHitRateTracker.init();
    enemyHitRateTracker.init();
    roundNum = bot.getRoundNum();
    enemyIsAlive = true;
  }

  public void updateWithoutScan() {
    updateMyStates();
    while (enemyState.gameTime < gameTime) {  // check if enemy info is out of date
      enemyState = enemyState.getNextState(0, 0);
      if (bot.getOthers() == 0) {
        enemyIsAlive = false;
        enemyState.energy = 0;
      }
      updateStates(enemyState, enemyHistory);
    }
    setFutureStates();
  }

  public void update(ScannedRobotEvent e) {
    updateMyStates();
    BotState prevEnemyState = enemyState;
    enemyState = new BotState(
        Geom.project(myState.location,
            bot.getHeadingRadians() + e.getBearingRadians(), e.getDistance()),
        e.getHeadingRadians(),
        e.getVelocity(),
        e.getEnergy(),
        prevEnemyState == null ? bot.getGunHeat() :
          prevEnemyState.gunHeat - bot.getGunCoolingRate(),
        enemyHitRateTracker.getShots(),
        prevEnemyState == null ? 0 : prevEnemyState.lastFireTime,
        bot.getTime());
    updateStates(enemyState, enemyHistory);
    String[] split = e.getName().split(" ");
    enemyName = String.join(" ", Arrays.asList(split).subList(0, Math.min(2, split.length)));

    if (myHistory.size() == 1) {
      return;
    }

    // detect drops in the enemy energy indicating that they have fired
    double wallDamage = 0;
    if (enemyFuture.size() > 0 &&
        Math.abs(enemyState.velocity) == 0 &&
        Math.abs(prevEnemyState.velocity) > 2 &&
        BattleField.INSTANCE.wallDistance(enemyState.location) < 20) {
      wallDamage = Math.max(0, (Math.abs(enemyFuture.get(0).velocity) / 2) - 1);
    }
    double zapDamage = myHistory.size() < 2 ? 0 : myHistory.get(1).energy - myState.energy;
    double energyDifference = prevEnemyState.energy - enemyState.energy - wallDamage - zapDamage;
    enemyFiredLastTick = (enemyState.energy == 0 || energyDifference > 0.09999) &&
        energyDifference < 3.0001 && prevEnemyState.gunHeat < 1e-4;
    if (enemyFiredLastTick) {
      if (!enemyFiredThisRound) {
        enemyFirstShotBulletPower = energyDifference;
        enemyFiredThisRound = true;
      }
      lastEnemyBulletPower = energyDifference;
      prevEnemyState.updateFromFire(energyDifference);
      enemyState.gunHeat = prevEnemyState.gunHeat - bot.getGunCoolingRate();
      enemyState.lastFireTime = prevEnemyState.lastFireTime;
      enemyState.shotsFired = prevEnemyState.shotsFired;
      enemyHitRateTracker.logShot();
    }
    if (enemyState.gunHeat < -10 * bot.getGunCoolingRate() &&
        myState.energy - enemyState.energy < 10) {
      // hack to lower our bullet power if the enemy has stopped firing and we have low energy
      lastEnemyBulletPower = 0.1;
    }

    setFutureStates();
  }

  public void setMyFutureStates(List<BotState> states) {
    myFuture = states;
  }

  public BotState getMyState(int tick) {
    return getState(true, tick);
  }

  public BotState getEnemyState(int tick) {
    return getState(false, tick);
  }

  public void onFire(double power) {
    myState.updateFromFire(power);
    myHitRateTracker.logShot();
  }

  public void onBulletHit(BulletHitEvent e) {
    updateFromBulletHit(e.getBullet().getPower(), myState, enemyState, myHitRateTracker);
  }

  public void onHitByBullet(HitByBulletEvent e) {
    updateFromBulletHit(e.getPower(), enemyState, myState, enemyHitRateTracker);
  }

  public void onHitRobot(HitRobotEvent e) {
    if (myHistory.size() > 0) {
      myState.energy -= 0.6;
      enemyState.energy -= 0.6;
      if (e.isMyFault()) {
        lastCollideTime = bot.getTime();
      } else {
        lastEnemyCollideTime = bot.getTime();
      }
    }
  }

  public void onRoundEnd(boolean won) {
    myHitRateTracker.onRoundEnd(won);
    enemyHitRateTracker.onRoundEnd(!won);
  }

  private BotState getState(boolean mine, int tick) {
    List<BotState> states;
    if (tick <= 0) {
      states = mine ? myHistory : enemyHistory;
      tick = -tick;
    } else {
      states = mine ? myFuture : enemyFuture;
      tick -= 1;
    }
    return states.isEmpty() ? (mine ? myState : enemyState) :
      states.get(Math.min(states.size() - 1, tick));
  }

  private void updateStates(BotState newState, List<BotState> previousStates) {
    previousStates.add(0, newState);
    if (previousStates.size() > HISTORY_SIZE) {
      previousStates.remove(previousStates.size() - 1);
    }
  }

  private void updateMyStates() {
    gameTime = bot.getTime();
    enemyFiredLastTick = false;
    if (myState == null || gameTime != myState.gameTime) {
      myState = new BotState(
          new Point2D.Double(bot.getX(), bot.getY()),
          bot.getHeadingRadians(),
          bot.getVelocity(),
          bot.getEnergy(),
          myState == null ? bot.getGunHeat() :
            myState.gunHeat - bot.getGunCoolingRate(),
            myHitRateTracker.getShots(),
            myState == null ? 0 : myState.lastFireTime,
          bot.getTime());
      updateStates(myState, myHistory);
    }
  }

  private void setFutureStates() {
    if (myFuture.size() > 0) {
      BotState predictedState = myFuture.remove(0);
      // our predicted future state was wrong, so reset the prediction
      if (Math.abs(myState.location.x - predictedState.location.x) +
          Math.abs(myState.location.y - predictedState.location.y) +
          Math.abs(Utils.normalRelativeAngle(myState.heading - predictedState.heading)) +
          Math.abs(myState.velocity - predictedState.velocity) > 1e-4) {
        myFuture.clear();
      }
    }
    enemyFuture.clear();
    addPredictedFutureStates(myHistory, myFuture);
    addPredictedFutureStates(enemyHistory, enemyFuture);
  }

  private void addPredictedFutureStates(List<BotState> history, List<BotState> future) {
    int currentInd = future.size() - 1;
    int prevInd = future.size() - 2;
    BotState currentState = currentInd >= 0 ? future.get(currentInd) : history.get(-currentInd - 1);
    BotState prevState = prevInd >= 0 ? future.get(prevInd) : history.get(-prevInd - 1);
    while (future.size() < FUTURE_SIZE) {
      BotState nextState = currentState.predictNextState(prevState);
      future.add(nextState);
      prevState = currentState;
      currentState = nextState;
    }
  }

  private void updateFromBulletHit(
      double power, BotState hitterState, BotState hitBotState, HitRateTracker hitRateTracker) {
    hitBotState.updateFromHitByBullet(power);
    hitterState.updateFromBulletHit(power);
    hitRateTracker.logHit(Physics.bulletDamage(power, hitBotState.energy));
    hitRateTracker.logShotPassed(power);
  }
}
