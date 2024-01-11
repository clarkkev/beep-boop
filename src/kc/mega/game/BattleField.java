package kc.mega.game;

import kc.mega.utils.Geom;
import kc.mega.utils.MathUtils;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import jk.math.FastTrig;
import robocode.AdvancedRobot;

/** Represents robocode battlefield and handles wall collisions/avoidance. */
public enum BattleField {
  INSTANCE;

  private static final double FANCY_SMOOTH_MARGIN = 18.1;
  private static final double TURNING_RADIUS = 91 * 8 / (2 * Math.PI);

  private double battleFieldWidth, battleFieldHeight;
  private double N, S, E, W;
  private Rectangle2D.Double battleField;
  private Point2D.Double center;
  private double gunCoolingRate;

  public void onBattleStart(AdvancedRobot bot) {
    battleFieldWidth = bot.getBattleFieldWidth();
    battleFieldHeight = bot.getBattleFieldHeight();
    battleField = makeField(battleFieldWidth, battleFieldHeight, 18.0);
    center = new Point2D.Double(battleFieldWidth / 2, battleFieldHeight / 2);
    gunCoolingRate = bot.getGunCoolingRate();
    N = battleFieldHeight - FANCY_SMOOTH_MARGIN;
    S = FANCY_SMOOTH_MARGIN;
    E = battleFieldWidth - FANCY_SMOOTH_MARGIN;
    W = FANCY_SMOOTH_MARGIN;
  }

  private static Rectangle2D.Double makeField(double width, double height, double margin) {
    return new Rectangle2D.Double(margin, margin, width - (margin * 2), height - (margin * 2));
  }

  public Point2D.Double getCenter() {
    return center;
  }

  public Rectangle2D.Double getFieldRectangle() {
    return battleField;
  }

  public double getGunCoolingRate() {
    return gunCoolingRate;
  }

  public Point2D.Double mirrorLocation(Point2D.Double location) {
    return new Point2D.Double(2 * center.x - location.x, 2 * center.y - location.y);
  }

  public PredictState wallCollisionCheck(PredictState state) {
    if (!battleField.contains(state.location)) {
      return new PredictState(
          new Point2D.Double(MathUtils.clip(state.location.x, 18.0, battleFieldWidth - 18.0),
                             MathUtils.clip(state.location.y, 18.0, battleFieldHeight - 18.0)),
          state.heading, 0.0, state.gameTime);
    }
    return state;
  }

  public double wallDistance(Point2D.Double location) {
    return Math.min(Math.min(location.x, location.y),
        Math.min(battleFieldWidth - location.x, battleFieldHeight - location.y));
  }

  public double orbitalWallDistance(
      BotState myState, BotState enemyState, int orbitDirection, double maxEscapeAngle) {
    double absoluteBearing = myState.absoluteBearing(enemyState);
    double distance = myState.distance(enemyState);
    double wallDistance = 0;
    double hi = 0.0, lo = (1.5 * maxEscapeAngle) + 0.01;
    for (int i = 0; i < 12; i++) {
      wallDistance = (lo + hi) / 2;
      Point2D.Double enemyProjectedLocation = Geom.project(
          myState.location, absoluteBearing + (orbitDirection * wallDistance), distance);
      if (battleField.contains(enemyProjectedLocation)) {
        lo = wallDistance;
      } else {
        hi = wallDistance;
      }
    }
    return Math.min(wallDistance / maxEscapeAngle, 1.5);
  }

  // Non-iterative walking stick wall smoothing by David Alves
  public double walkingStickSmooth(Point2D.Double location, double heading,
      int direction, int orbitDirection, double stickLength, double margin) {
    boolean top = false, right = false, bottom = false, left = false;
    int smoothedWall = 0;//1 = top, 2 = right, 3 = bottom, 4 = left

    Point2D.Double projectedLocation = Geom.project(location, heading, stickLength * direction);
    if(projectedLocation.getX() < margin) {
      left = true;
    } else if(projectedLocation.getX() > battleFieldWidth - margin) {
      right = true;
    }
    if(projectedLocation.getY() < margin) {
      bottom = true;
    } else if(projectedLocation.getY() > battleFieldHeight - margin){
      top = true;
    }

    if(top) {
      if(right) {
        if(orbitDirection == 1) {
          smoothedWall = 2;
        } else {
          smoothedWall = 1;
        }
      } else if(left) {
        if(orbitDirection == 1) {
          smoothedWall = 1;
        }
        else {
          smoothedWall = 4;
        }
      } else {
        smoothedWall = 1;
      }
    }
    else if(bottom) {
      if(right) {
        if(orbitDirection == 1) {
          smoothedWall = 3;
        }
        else {
          smoothedWall = 2;
        }
      } else if(left) {
        if(orbitDirection == 1) {
          smoothedWall = 4;
        }
        else {
          smoothedWall = 3;
        }
      } else {
        smoothedWall = 3;
      }
    } else {
      if(right) {
        smoothedWall = 2;
      } else if(left) {
        smoothedWall = 4;
      }
    }

    if(smoothedWall == 1) {
      heading = orbitDirection * FastTrig.acos(
          (battleFieldHeight - margin - location.getY()) / stickLength);
    } else if(smoothedWall == 2) {
      heading = (Math.PI/2) + (orbitDirection * FastTrig.acos(
          (battleFieldWidth - margin - location.getX()) / stickLength));
    } else if(smoothedWall == 3) {
      heading = Math.PI + (orbitDirection * FastTrig.acos(
          (location.getY() - margin) / stickLength));
    } else if(smoothedWall == 4) {
      heading = (3*Math.PI/2) + (orbitDirection * FastTrig.acos(
          (location.getX() - margin) / stickLength));
    }
    if(direction == -1 && smoothedWall != 0) {
      heading += Math.PI;
    }

    return heading;
  }

  // Improved version of Simonton's fancy stick wall smoothing
  public double fancyStickSmooth(
      double heading, double speed, double x, double y, int orbitDirection) {
    if(heading > Math.PI) { // left
      if(shouldSmooth(heading - Math.PI, speed, x - W, orbitDirection)) {
         heading = smoothAngle(speed, x - W, orbitDirection) + Math.PI;
      }
    } else if(heading < Math.PI) { //right
      if(shouldSmooth(heading, speed, E - x, orbitDirection)) {
        heading = smoothAngle(speed, E - x, orbitDirection);
      }
    }
    if(heading < Math.PI/2 || heading > 3*Math.PI/2) { //top
      if(shouldSmooth(heading + Math.PI/2, speed, N - y, orbitDirection)) {
        heading = smoothAngle(speed, N - y, orbitDirection) - Math.PI/2;
      }
    } else if(heading > Math.PI/2 && heading < 3*Math.PI/2) { //bottom
      if(shouldSmooth(heading - Math.PI/2, speed, y - S, orbitDirection)) {
        heading = smoothAngle(speed, y - S, orbitDirection) + Math.PI/2;
      }
    }
    return heading;
  }

  private double smoothAngle(double speed, double wallDistance, int orbitDirection) {
    if (wallDistance < 0.01) {
      return orbitDirection == 1 ? Math.PI : 0;
    }
    double heading = FastTrig.acos((wallDistance - TURNING_RADIUS) / Math.sqrt(
        MathUtils.sqr(TURNING_RADIUS) + MathUtils.sqr(speed))) +
        FastTrig.atan(speed / TURNING_RADIUS);
    return (orbitDirection == 1 ? heading : Math.PI - heading);
  }

  private boolean shouldSmooth(
      double heading, double speed, double wallDistance, int orbitDirection) {
    wallDistance -= (speed * FastTrig.sin(heading));
    if (wallDistance < 0) {
      return true;
    }
    wallDistance -= (1 + FastTrig.sin(heading + (orbitDirection * Math.PI/2))) * TURNING_RADIUS;
    return wallDistance < 0;
  }
}
