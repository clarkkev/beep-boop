package kc.mega.scan;

import kc.mega.game.BattleField;
import kc.mega.game.GameState;
import kc.mega.utils.Geom;
import kc.mega.utils.MathUtils;

import java.awt.geom.Point2D;

import robocode.AdvancedRobot;

/** Controls BeepBoop's radar. */
public class Scanner {
  private static final double SCAN_WIDTH = Math.PI / 32;
  private static final int REVERALS_BEFORE_SWEEP = 3;

  private final AdvancedRobot bot;
  private final GameState gs;
  private int radarTurnDirection;
  private int radarReversals;
  private boolean hasScanned;

  public Scanner(AdvancedRobot bot, GameState gs) {
    this.bot = bot;
    this.gs = gs;
  }

  public void onRoundStart() {
    hasScanned = false;
    radarReversals = 0;
    // turn gun and radar towards the center at start of the round to detect the other bot quickly
    radarTurnDirection = MathUtils.nonzeroSign(Geom.offset(
        new Point2D.Double(bot.getX(), bot.getY()), BattleField.INSTANCE.getCenter(),
        bot.getRadarHeadingRadians()));
    bot.setTurnGunRightRadians(radarTurnDirection * Double.POSITIVE_INFINITY);
    bot.setTurnRadarRightRadians(radarTurnDirection * Double.POSITIVE_INFINITY);
  }

  public void search() {
    // if we lose sight of the other bot (due to skipped turns) try scanning back to where we last
    // saw them a few times before doing a full sweep
    if (radarReversals < REVERALS_BEFORE_SWEEP) {
      int newRadarTurnDirection = MathUtils.nonzeroSign(
          gs.myState.offset(gs.enemyState, bot.getRadarHeadingRadians()));
      if (newRadarTurnDirection != radarTurnDirection) {
        radarTurnDirection = newRadarTurnDirection;
        radarReversals++;
      }
    }
    bot.setTurnRadarRightRadians(radarTurnDirection * Double.POSITIVE_INFINITY);
  }

  public void scan() {
    if (!hasScanned) {
      bot.setTurnGunRightRadians(-radarTurnDirection * Math.PI / 10);
      hasScanned = true;
    }
    radarReversals = 0;
    double radarTurn = gs.myState.offset(gs.enemyState, bot.getRadarHeadingRadians());
    bot.setTurnRadarRightRadians(radarTurn + MathUtils.nonzeroSign(radarTurn) * SCAN_WIDTH);
  }
}
