package kc.mega;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import kc.mega.aim.Aimer;
import kc.mega.game.BattleField;
import kc.mega.game.GameState;
import kc.mega.game.Physics;
import kc.mega.move.Mover;
import kc.mega.scan.Scanner;
import kc.mega.shared.Strategy;
import kc.mega.shared.WaveManager;
import kc.mega.shield.Shielder;
import kc.mega.utils.DatasetWriter;
import kc.mega.utils.Painter;
import kc.mega.wave.Bullet;
import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.ScannedRobotEvent;
import robocode.SkippedTurnEvent;
import wiki.mc2k7.RaikoGun;

/**
 * BeepBoop - a robocode robot by Kevin Clark (Kev)
 * See https://robowiki.net/wiki/BeepBoop for details.
 */
public class BeepBoop extends AdvancedRobot {
  private static boolean SHIELD = true;  // https://robowiki.net/wiki/Bullet_Shielding
  private static boolean MC2k7 = false;  // https://robowiki.net/wiki/Movement_Challenge_2K7
  private static boolean TC = false;  // https://robowiki.net/wiki/Category:Targeting_Challenges
  private static boolean MC = false;  // https://robowiki.net/wiki/Category:Movement_Challenges
  private static boolean VERBOSE = false;  // print out additional match statistics

  private static GameState gs;
  private static WaveManager waveManager;
  private static Strategy strategy;
  private static Scanner scanner;
  private static Shielder shielder;
  private static Mover mover;
  private static Aimer aimer;
  private static RaikoGun raikoGun;  // for MC2k7

  private static int[] finishes;
  private static int skippedTurns;
  private boolean hasWon;
  private boolean hasDied;
  private long lastPaintTime = -1;
  private ScannedRobotEvent lastScanEvent;
  private Graphics2D graphics;

  @Override
  public void run() {
    if (getRoundNum() == 0) {
      onBattleStart();
    }
    onRoundStart();
    do {
      doTurn();
      Painter.paint(graphics, lastPaintTime == getTime());
      execute();
    } while(true);
  }

  private void onBattleStart() {
    BattleField.INSTANCE.onBattleStart(this);
    DatasetWriter.INSTANCE.onBattleStart(this);
    gs = new GameState(this);
    waveManager = new WaveManager();
    strategy = new Strategy(this, gs, waveManager, !TC && !MC && SHIELD);
    scanner = new Scanner(this, gs);
    mover = new Mover(this, strategy);
    aimer = new Aimer(this, strategy, mover, TC);
    shielder = new Shielder(this, gs, waveManager.movementWaves);
    finishes = new int[getOthers() + 1];
    if (MC2k7) {
      TC = false;
      MC = true;
      raikoGun = new RaikoGun(this);
    }
  }

  private void onRoundStart() {
    System.out.println("Beep Boop!");
    setColors(new Color(20, 25, 35), new Color(20, 25, 35), new Color(0, 255, 128));
    setAdjustRadarForGunTurn(true);
    setAdjustGunForRobotTurn(true);
    gs.onRoundStart();
    waveManager.onRoundStart();
    scanner.onRoundStart();
    if (!MC) {
      aimer.onRoundStart();
    }
    if (!TC) {
      mover.onRoundStart();
    }
    if (strategy.shield) {
      shielder.onRoundStart();
    }
  }

  private void doTurn() {
    if (lastScanEvent == null || hasDied) {
      return;
    }
    if (getOthers() == 0) {
      setRadarColor(Color.getHSBColor((getTime() % 20) / 20.0f, 1.0f, 1.0f));  // celebrate winning
      if (!hasWon) {
        hasWon = true;
        onRoundEnd();
      }
    }
    DatasetWriter.INSTANCE.setEnemyName(lastScanEvent.getName());
    if (lastScanEvent.getTime() == getTime()) {
      gs.update(lastScanEvent);
      scanner.scan();
    } else {
      gs.updateWithoutScan();
      scanner.search();
    }
    strategy.strategize();
    if (strategy.shield) {
      shielder.shield();
    } else {
      if (!TC) {
        mover.move();
      }
      if (!MC) {
        aimer.aim();
      } else if (MC2k7 && lastScanEvent.getTime() == getTime()) {
        raikoGun.onScannedRobot(lastScanEvent);
      }
    }
  }

  private void onRoundEnd() {
    gs.onRoundEnd(hasWon);
    if (!strategy.shield) {
      if (!MC) {
        aimer.printStats(VERBOSE);
      }
      if (!TC) {
        mover.printStats(VERBOSE);
      }
    }
    System.out.println("Skipped Turns: " + skippedTurns);
    System.out.print("Finishes: ");
    finishes[getOthers()]++;
    for (int i = 0; i < finishes.length; i++) {
      System.out.print((i == getOthers() ? "*" : "") + finishes[i] + " ");
    }
    System.out.println();
    DatasetWriter.INSTANCE.onRoundEnd();
  }

  @Override
  public void onScannedRobot(ScannedRobotEvent e) {
    lastScanEvent = e;
  }

  @Override
  public void onBulletHit(BulletHitEvent e) {
    gs.onBulletHit(e);
    if (!MC && !strategy.shield) {
      aimer.onBulletHit(e);
    }
  }

  @Override
  public void onHitByBullet(HitByBulletEvent e) {
    gs.onHitByBullet(e);
    if (strategy.shield) {
      shielder.onHitByBullet(e);
    } else if (!TC) {
      mover.onHitByBullet(e);
    }
  }

  @Override
  public void onBulletHitBullet(BulletHitBulletEvent e) {
    if (strategy.shield) {
      shielder.onBulletHitBullet(e);
    } else {
      waveManager.onBulletHitBullet();
      if (!TC) {
        mover.onBulletHitBullet(e);
      }
      if (!MC) {
        aimer.onBulletHitBullet(e);
      }
    }
  }

  @Override
  public void onHitRobot(HitRobotEvent e) {
    gs.onHitRobot(e);
  }

  @Override
  public void onSkippedTurn(SkippedTurnEvent e) {
    gs.updateWithoutScan();
    skippedTurns++;
  }

  @Override
  public void onDeath(DeathEvent e) {
    hasDied = true;
    if (!hasWon) {
      onRoundEnd();
    }
  }

  @Override
  public void onPaint(Graphics2D g) {
    lastPaintTime = getTime();
    graphics = g;
  }

  @Override
  public void setFire(double power) {
    if (setFireBullet(power) != null) {
      gs.onFire(power);
      if (!strategy.shield) {
        waveManager.onFire(new Bullet(new Point2D.Double(getX(), getY()), getGunHeadingRadians(),
            Physics.bulletSpeed(power), getTime()));
      }
    }
  }
}
