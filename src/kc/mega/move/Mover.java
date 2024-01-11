package kc.mega.move;

import kc.mega.game.BotState;
import kc.mega.game.GameState;
import kc.mega.move.wave.MovementWave;
import kc.mega.move.wave.MovementWaves;
import kc.mega.shared.Strategy;
import kc.mega.shared.WaveManager;
import kc.mega.utils.Painter;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import jk.math.FastTrig;
import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.HitByBulletEvent;

/** Controls BeepBoop's wave surfing (https://robowiki.net/wiki/Wave_Surfing) movement. */
public class Mover {
  public final AdvancedRobot bot;
  public final GameState gs;
  public final Strategy strategy;
  public final WaveManager waveManager;
  public final MovementWaves movementWaves;
  public final PathSurfer surfer;
  public final DangerEstimator dangerEstimator;
  private MovementWave currentVirtualWave;

  public Mover(AdvancedRobot bot, Strategy strategy) {
    this.bot = bot;
    this.strategy = strategy;
    gs = strategy.gs;
    waveManager = strategy.waveManager;
    movementWaves = waveManager.movementWaves;
    dangerEstimator = new DangerEstimator(strategy, gs.enemyHitRateTracker);
    surfer = new PathSurfer(bot, strategy, dangerEstimator);
  }

  public void onRoundStart() {
    surfer.clearState();
    currentVirtualWave = null;
  }

  public void move() {
    if (gs.enemyFiredThisRound) {
      makeMovementWave();
    }
    for (MovementWave w : movementWaves.update(gs.myState)) {
      if (!w.didHit && !w.isVirtual && (w.hasBullet || w.didCollide)) {
        gs.enemyHitRateTracker.logShotPassed(w.power);
        //datasetWriter.write("move-data-shots", w.toArray());
      }
      if (gs.enemyIsAlive) {
        //datasetWriter.write("move-data-visits", w.toArray());
        dangerEstimator.onVisit(w);
      }
    }
    dangerEstimator.onTurn(gs, movementWaves);
    if (Painter.active) {
      dangerEstimator.paint();
    }
    surfer.surf(strategy.ram ? new ArrayList<>() : movementWaves.getSurfableWaves(gs.myState));
  }

  public void makeMovementWave() {
    MovementWave w = (MovementWave)new MovementWave.Builder()
        .isVirtual(false)
        .waves(movementWaves)
        .myHistory(gs.enemyHistory.subList(1, gs.enemyHistory.size()))
        .enemyHistory(gs.myHistory.subList(1, gs.myHistory.size()))
        .power(gs.lastEnemyBulletPower)
        .hasBullet(gs.enemyFiredLastTick)
        .build();
    movementWaves.add(w);
    if (w.hasBullet) {
      waveManager.onEnemyFire(w);
      if (currentVirtualWave != null) {
        movementWaves.remove(currentVirtualWave);
        currentVirtualWave = null;
      }
    }
    double lastEnemyEnergy = gs.getEnemyState(-1).energy;
    if (lastEnemyEnergy <= 0) {
      return;
    }

    // Create gun heat waves (https://robowiki.net/wiki/Gun_Heat_Waves)
    List<BotState> myWaveStates = new ArrayList<>(gs.myHistory);
    List<BotState> enemyWaveStates = new ArrayList<>(gs.enemyHistory);
    double predictedBulletPower = Math.min(gs.lastEnemyBulletPower, lastEnemyEnergy);
    for (int i = 0; i < 2; i++) {
      BotState futureEnemyState = gs.getEnemyState(i);
      if (futureEnemyState.gunHeat < 0.0001) {
        MovementWave virtualWave = (MovementWave)new MovementWave.Builder()
            .isVirtual(true)
            .waves(movementWaves)
            .myHistory(enemyWaveStates)
            .enemyHistory(myWaveStates)
            .power(predictedBulletPower)
            .build();
        movementWaves.remove(currentVirtualWave);
        movementWaves.add(virtualWave);
        currentVirtualWave = virtualWave;
        waveManager.onEnemyFire(virtualWave);
        break;
      }
      myWaveStates.add(0, gs.getMyState(i));
      enemyWaveStates.add(0, gs.getEnemyState(i));
    }
  }

  public void onHitByBullet(HitByBulletEvent e) {
    MovementWave hitWave = movementWaves.getHitWave(e.getPower());
    if (hitWave != null && hitWave.fireTime < bot.getTime() - 1) {
      learnFromBullet(hitWave, e.getHeadingRadians());
    }
  }

  public void onBulletHitBullet(BulletHitBulletEvent e) {
    MovementWave hitWave = movementWaves.getBulletHitBulletWave(
        new Point2D.Double(e.getHitBullet().getX(), e.getHitBullet().getY()),
        e.getHitBullet().getPower(), bot.getTime());
    if (hitWave != null) {
      learnFromBullet(hitWave, e.getHitBullet().getHeadingRadians());
    }
  }

  private void learnFromBullet(MovementWave w, double bulletHeading) {
    w.hitOffset = FastTrig.normalRelativeAngle(bulletHeading - w.absoluteBearing);
    if (Math.abs(w.hitGF()) > 0.1) {
      strategy.onNonHeadOnHit();
    }
    dangerEstimator.onSeeBullet(w);
    //datasetWriter.INSTANCE.write("move-data-hits", hitWave.asArray());
  }

  public void printStats(boolean verbose) {
    if (verbose) {
      dangerEstimator.printEstimatorWeights();
    }
    System.out.println("Enemy Hit Rate: " + gs.enemyHitRateTracker.getHitRateStr());
  }
}
