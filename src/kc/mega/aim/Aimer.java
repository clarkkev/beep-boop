package kc.mega.aim;

import kc.mega.aim.models.AimModels;
import kc.mega.aim.models.KNNAimModel;
import kc.mega.aim.wave.AimWave;
import kc.mega.aim.wave.AimWaves;
import kc.mega.game.BotState;
import kc.mega.game.GameState;
import kc.mega.move.Mover;
import kc.mega.move.ShadowScorer;
import kc.mega.shared.Strategy;
import kc.mega.utils.Painter;
import kc.mega.wave.Wave;
import kc.mega.wave.WaveWithFeatures;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.util.Utils;

/** Main class for aiming the turret and firing. */
public class Aimer {
  private static final int ANGLES_TO_SCORE = 15;

  private final AdvancedRobot bot;
  private final GameState gs;
  private final Strategy strategy;
  private final AimWaves aimWaves;
  private final boolean TC;
  private final ShadowScorer shadowScorer;
  private final KNNAimModel antiRandomModel;
  private final KNNAimModel antiSurferModel;

  private KNNAimModel firingModel;
  private boolean hasFired;
  private int shadowShots;
  private int aimShots;
  private boolean isShadowing;
  private double bulletPower;

  public Aimer(AdvancedRobot bot, Strategy strategy, Mover mover, boolean tc) {
    this.bot = bot;
    this.strategy = strategy;
    this.TC = tc;
    gs = strategy.gs;
    aimWaves = strategy.waveManager.aimWaves;
    shadowScorer = mover == null ? null : new ShadowScorer(mover.surfer);
    antiRandomModel = AimModels.getMainModel();
    antiSurferModel = AimModels.getAntiSurferModel();
    firingModel = antiRandomModel;
  }

  public void onRoundStart() {
    hasFired = false;
    if (!strategy.shield) {
      System.out.println("Using " + firingModel.name + " aiming model");
    }
  }

  public void aim() {
    BotState myState = gs.myState;
    for (AimWave w : aimWaves.update(gs.enemyState)) {
      if (gs.enemyIsAlive) {
        antiRandomModel.train(w);
        antiSurferModel.train(w);
        if (!w.didHit && (w.hasBullet || (w.virtuality == 0 && w.didCollide))) {
          gs.myHitRateTracker.logShotPassed(w.power);
        }
        //DatasetWriter.INSTANCE.write("gun-data", w.toArray());
      }
    }

    double currentBulletPower = Math.min(firingModel.hasData() ?
        3 : 0.15, BulletPowerSelector.bestBulletPower(gs, strategy));
    if (TC) {
      currentBulletPower = bulletPower = Math.min(3.0, myState.energy);
    } else if (!gs.enemyIsAlive) {
      currentBulletPower = myState.energy > 1 ? 0.1 : 0;  // 0.1 power for shadowing after a win
    } else if (strategy.ram) {
      currentBulletPower = 0;
    }
    boolean doFire = false;
    if ((currentBulletPower >= myState.energy ||  // edge case: disable ourselves to shoot a rambot
        // don't fire if the optimal bullet power changed drastically and we have low energy
        (myState.energy - bulletPower) / (myState.energy - currentBulletPower) > 0.9) &&
        currentBulletPower > 0.099999 && bulletPower > 0.099999 && myState.gunHeat <= 0.0 &&
        Math.abs(bot.getGunTurnRemainingRadians()) < 0.001 && (gs.enemyIsAlive || isShadowing)) {
      hasFired = true;
      doFire = true;
      if (isShadowing) {
        shadowShots++;
      } else {
        aimShots++;
      }
      // fire with the previous tick's bullet power (when aiming occurred)
      bot.setFire(bulletPower);
    }

    if (hasFired && bulletPower > 0.099999) {
      aimWaves.add((AimWave)new AimWave.Builder()
          .gunHeading(bot.getGunHeadingRadians())
          .waves(aimWaves)
          .myHistory(gs.myHistory)
          .enemyHistory(gs.enemyHistory)
          .power(bulletPower)
          .hasBullet(doFire)
          .build());
    }
    bulletPower = currentBulletPower;

    bot.setTurnGunRightRadians(gs.getMyState(1).offset(gs.enemyState, bot.getGunHeadingRadians()));
    int ticksUntilFire = (int)Math.ceil(myState.gunHeat / bot.getGunCoolingRate());
    if (!doFire && ticksUntilFire <= 3) {  // only aim when we are close to firing
      List<BotState> myNextHistory = new ArrayList<>(gs.myHistory);
      List<BotState> enemyNextHistory = new ArrayList<>(gs.enemyHistory);
      myNextHistory.add(0, gs.getMyState(1));
      enemyNextHistory.add(0, null);
      // next tick's wave, used for aiming
      WaveWithFeatures w = (WaveWithFeatures)new WaveWithFeatures.Builder()
          .waves(aimWaves)
          .myHistory(myNextHistory)
          .enemyHistory(enemyNextHistory)
          .power(bulletPower)
          .hasBullet(true)
          .build();

      if (ticksUntilFire > 1) {
        pointGun(w, ticksUntilFire > 1 ? firingModel.getAimGFFast(w) :
          firingModel.getAimGFs(w, 1).get(0));
      } else {
        // candidate aim guessfactors for active bullet shadowing
        List<Double> GFs = new ArrayList<>();
        if (gs.enemyIsAlive) {
          GFs = firingModel.getAimGFs(w, ANGLES_TO_SCORE);
        } else {
          GFs.add(0.0);
          for (int i = 0; i < ANGLES_TO_SCORE - 1; i++) {
            GFs.add(2 * Math.random() - 1);
          }
        }
        GFs.addAll(shadowScorer.getHelpfulShadowGFs(w));

        // pick the highest-scoring guessfactor to aim at
        List<Double> dangers = shadowScorer.getDangersAfterNewShadows(GFs, w);
        double bodyTurn = gs.getMyState(1).heading - myState.heading;
        double[] maxGunTurn = {Utils.normalRelativeAngle(bodyTurn - Math.PI / 9.01),
                               Utils.normalRelativeAngle(bodyTurn + Math.PI / 9.01)};
        List<Double> scores = new ArrayList<>();
        double bestAimScore = 0;
        int bestInd = 0;
        for (int i = 0; i < GFs.size(); i++) {
          double GF = GFs.get(i);
          double aimScore = gs.enemyIsAlive ? firingModel.scoreAimGF(GF) : (i == 0 ? 0.301 : 0.3);
          bestAimScore = Math.max(aimScore, bestAimScore);
          // lower weight for bullet shadowing when enemy is inaccurate or using low-power bullets
          double score = aimScore / Math.pow(dangers.get(i), 2.0 * Math.pow((
              Math.max(0.001, gs.enemyHitRateTracker.getHitRate()) /
              Math.max(0.001, gs.myHitRateTracker.getHitRate())) *
              (gs.lastEnemyBulletPower / bulletPower), 0.25));
          double gunTurn = getGunTurn(w, GF);
          if (gunTurn > maxGunTurn[0] && gunTurn < maxGunTurn[1]) {
            score *= 1.1; // bonus for picking reachable GF (otherwise we have to delay firing)
          }
          scores.add(score);
          if (score > scores.get(bestInd)) {
            bestInd = i;
          }
        }
        pointGun(w, GFs.get(bestInd));

        isShadowing = Math.abs(GFs.get(bestInd) - GFs.get(0)) > 0.01;

        // paint the considered aim GFs
        Painter.CUSTOM.clear();
        if (Painter.active && (gs.enemyIsAlive || isShadowing)) {
          if (gs.enemyIsAlive) {
            firingModel.paint();
          }
          double best = scores.get(bestInd);
          for (int i = 0; i < GFs.size(); i++) {
            Painter.CUSTOM.addPoint(Color.gray.brighter(), new Point2D.Double(
                130 + 100 * GFs.get(i), scores.get(i) * 100 * bestAimScore / best), 2);
          }
          Painter.CUSTOM.addPoint(isShadowing ? Color.green : Color.yellow, new Point2D.Double(
              130 + 100 * GFs.get(bestInd), scores.get(bestInd) * 100 * bestAimScore / best), 2);
        }
      }
    } else {
      pickAimModel();
    }
  }

  private void pointGun(Wave w, double GF) {
    bot.setTurnGunRightRadians(getGunTurn(w, GF));
  }

  private double getGunTurn(Wave w, double GF) {
    return Utils.normalRelativeAngle(w.getBearing(GF) - bot.getGunHeadingRadians());
  }

  private void pickAimModel() {
    // simply select based on hit rate instead of using https://robowiki.net/wiki/Virtual_Guns
    firingModel = gs.myHitRateTracker.hitRateInBounds(0, 0.12) ? antiSurferModel : antiRandomModel;
  }

  public void onBulletHit(BulletHitEvent e) {
    AimWave hitWave = aimWaves.getHitWave(e.getBullet().getPower());
    if (hitWave != null) {
      for (AimWave w : aimWaves.get()) {
        if (!w.hasBullet && Math.abs(w.fireTime - hitWave.fireTime) <= 3) {
          w.didHit = true;
        }
      }
    }
  }

  public void onBulletHitBullet(BulletHitBulletEvent e) {
    if (gs.enemyIsAlive) {
      AimWave hitWave = aimWaves.getBulletHitBulletWave(
          new Point2D.Double(e.getBullet().getX(), e.getBullet().getY()),
          e.getBullet().getPower(), bot.getTime());
      if (hitWave != null) {
        for (AimWave w : aimWaves.get()) {
          if (!w.hasBullet && Math.abs(w.fireTime - hitWave.fireTime) <= 3) {
            w.didCollide = true;
          }
        }
      }
    }
  }

  public void printStats(boolean verbose) {
    if (verbose) {
      System.out.println(String.format("Active Shadow Percent: %.2f",
          100.0 * shadowShots / (shadowShots + aimShots)));
    }
    System.out.println("My Hit Rate: " + gs.myHitRateTracker.getHitRateStr());
  }
}
