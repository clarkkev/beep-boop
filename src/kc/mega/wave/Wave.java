package kc.mega.wave;

import kc.mega.game.BotState;
import kc.mega.game.Physics;
import kc.mega.game.PredictState;
import kc.mega.utils.Geom;
import kc.mega.utils.MathUtils;
import kc.mega.utils.Painter;
import kc.mega.utils.Range;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import jk.math.FastTrig;

/** Represents the possible locations of a bullet (see https://robowiki.net/wiki/Waves). */
public class Wave {
  public static final int MIDAIR = 0, BREAKING = 1, WILL_PASS_THIS_TICK = 2, PASSED = 3;
  public static final GFBins BINS = new GFBins(151);

  public final Point2D.Double source;
  public final long fireTime;
  public final double power;
  public final double speed;
  public final double absoluteBearing;
  public final int orbitDirection;
  public final double[] maePrecise;

  public int ticksUntilBreak;
  public double radius;
  public boolean hasBullet;
  public boolean didHit;
  public boolean didCollide;

  public double hitOffset = 1000;
  public Range visitOffsetRange = new Range();
  public boolean trackPreciseShadows;
  public double[] shadows;
  public Range shadow;

  public static class Builder {
    public List<BotState> myHistory;
    public List<BotState> enemyHistory;
    public double power;
    public boolean hasBullet = true;
    public boolean trackPreciseShadows = false;
    public int lastOrbitDirection = 1;

    public Builder myHistory(List<BotState> val) {myHistory = val; return this;}
    public Builder enemyHistory(List<BotState> val) {enemyHistory = val; return this;}
    public Builder power(double val) {power = val; return this;}
    public Builder hasBullet(boolean val) {hasBullet = val; return this;}
    public Builder trackPreciseShadows(boolean val) {trackPreciseShadows = val; return this;}
    public Builder lastOrbitDirection(int val) {lastOrbitDirection = val; return this;}
    public Wave build() {return new Wave(this);}
  }

  public Wave(Builder builder) {
    BotState myState = builder.myHistory.get(0);
    this.source = myState.location;
    this.fireTime = myState.gameTime;
    this.power = builder.power;
    this.speed = Physics.bulletSpeed(builder.power);
    this.hasBullet = builder.hasBullet;
    this.trackPreciseShadows = builder.trackPreciseShadows;
    this.didHit = false;
    this.didCollide = false;

    BotState enemyState = builder.enemyHistory.get(Math.min(1, builder.enemyHistory.size() - 1));
    int moveDirection = 0;
    for (int i = 1; i < builder.enemyHistory.size(); i++) {
      if (moveDirection == 0) {
        moveDirection = (int)Math.signum(builder.enemyHistory.get(i).velocity);
      } else {
        break;
      }
    }
    absoluteBearing = Geom.absoluteBearingPrecise(myState.location, enemyState.location);
    orbitDirection = moveDirection == 0 ? builder.lastOrbitDirection : Geom.orbitDirection(
        absoluteBearing, enemyState.heading, moveDirection);
    maePrecise = Geom.inFieldMaxEscapeAngles(myState.location, enemyState.location, speed);
    shadows = new double[BINS.nBins];
  }

  public int update(BotState enemyState) {
    ticksUntilBreak = getTicksUntilBreak(enemyState);
    radius = (enemyState.gameTime - fireTime) * speed;

    Foam foam = getFoam(enemyState);
    if (foam.status == BREAKING || foam.status == WILL_PASS_THIS_TICK) {
      visitOffsetRange.merge(foam.hitOffsetRange);
    }
    if (foam.status >= WILL_PASS_THIS_TICK && hitOffset > 100) {
      hitOffset = visitOffsetRange.middle();
    }
    return foam.status;
  }

  public double getGF(Point2D.Double location) {
    return getGF(Geom.offset(source, location, absoluteBearing));
  }

  public double getGF(double offset) {
    offset = FastTrig.normalRelativeAngle(offset);
    offset /= offset < 0 ? maePrecise[0] : maePrecise[1];
    offset *= orbitDirection;
    return MathUtils.clip(offset, -0.99999, 0.99999);
  }

  public Range getGFRange(Range offsetRange) {
    double GF1 = getGF(offsetRange.start);
    double GF2 = getGF(offsetRange.end);
    return Range.fromUnsorted(GF1, GF2);
  }

  public double hitGF() {
    return getGF(hitOffset);
  }

  public Range hitGFRange() {
   return getGFRange(visitOffsetRange);
  }

  public double getOffset(double GF) {
    GF *= orbitDirection;
    GF *= GF < 0 ? maePrecise[0] : maePrecise[1];
    return GF;
  }

  public double getBearing(double GF) {
    return absoluteBearing + getOffset(GF);
  }

  public void paintTick(Color color, double offset) {
    paintBearing(color, absoluteBearing + offset, radius - speed / 2, radius);
  }

  public void paintBearing(Color color, double bearing, double start, double stop) {
    Painter.THIS_TICK.addLine(color, Geom.project(source, bearing, start),
        Geom.project(source, bearing, stop));
  }

  // https://robowiki.net/wiki/Bullet_Shadow/Correct
  private boolean[] addBulletShadowsForTime(Bullet b, long gameTime, double[] s) {
    boolean bulletPassed = true;
    double[] newShadows = null;
    for (int bulletOffset = -1; bulletOffset <= 0; bulletOffset++) {
      for (int waveOffset = -1; waveOffset <= 0; waveOffset++) {
        if (bulletOffset == -1 && waveOffset == -1) {
          continue;
        }
        if (trackPreciseShadows && (bulletOffset != 0 || waveOffset != 0)) {
          continue;
        }
        if (gameTime - b.fireTime + bulletOffset <= 0 ||
            gameTime - fireTime + waveOffset <= 0) {
          bulletPassed = false;
          continue;
        }

        Point2D.Double bStart = b.getLocation(gameTime + bulletOffset);
        Point2D.Double bEnd = b.getLocation(gameTime + 1 + bulletOffset);
        double startDistance = bStart.distance(source);
        double endDistance = bEnd.distance(source);
        double startRadius = (gameTime - fireTime + waveOffset) * speed;
        double endRadius = (gameTime - fireTime + 1 + waveOffset) * speed;
        if (startDistance < startRadius || endDistance > startDistance) {
          continue;
        }
        bulletPassed = false;
        if (endDistance > endRadius) {
          continue;
        }

        Point2D.Double shadowStart, shadowEnd;
        if (startDistance <= endRadius) {
          shadowStart = bStart;
        } else {
          shadowStart = Geom.circleLineSegIntercept(bStart, bEnd, source, endRadius);
        }
        if (endDistance >= startRadius) {
          shadowEnd = bEnd;
        } else {
          shadowEnd = Geom.circleLineSegIntercept(bStart, bEnd, source, startRadius);
        }
        if (trackPreciseShadows) {
          shadow = Range.fromUnsorted(Geom.offsetPrecise(source, shadowStart, absoluteBearing),
                                      Geom.offsetPrecise(source, shadowEnd, absoluteBearing));
        }
        Range GFRange = getGFRange(new Range(
            Geom.offset(source, shadowStart, absoluteBearing),
            Geom.offset(source, shadowEnd, absoluteBearing)));
        if (GFRange.start < 0.999 || GFRange.end > -0.999) {
          if (newShadows == null) {
            newShadows = new double[BINS.nBins];
          }
          for (int bin = BINS.getBin(GFRange.start); bin <= BINS.getBin(GFRange.end); bin++) {
            if (waveOffset == bulletOffset) {
              newShadows[bin] += (1 - newShadows[bin]) * BINS.binWeight(GFRange, bin);
            } else {
              newShadows[bin] += 0.5 * BINS.binWeight(GFRange, bin);
            }
          }
        }
      }
    }

    if (newShadows != null) {
      for (int bin = 0; bin < newShadows.length; bin++) {
        if (newShadows[bin] > 0) {
          s[bin] += (1 - s[bin]) * newShadows[bin];
        }
      }
    }

    return new boolean[] {bulletPassed, newShadows == null};
  }

  public boolean addBulletShadows(Bullet b, double[] s) {
    boolean shadowsChanged = false;
    for (long time = Math.max(b.fireTime, fireTime); ; time++) {
      boolean[] result = addBulletShadowsForTime(b, time, s);
      shadowsChanged |= result[1];
      if (result[0]) {
        return shadowsChanged;
      }
    }
  }

  public boolean addBulletShadows(Bullet b) {
    return addBulletShadows(b, shadows);
  }

  public int getTicksUntilBreak(PredictState state) {
    int closeTicks = (int)(Geom.getMinDistanceToBot(source, state.location) / speed);
    return closeTicks - (int)(state.gameTime - fireTime);
  }

  public int getTicksUntilPasses(PredictState state) {
    return getTicksUntilPasses(state.location, state.gameTime);
  }

  public int getTicksUntilPasses(Point2D.Double location, long time) {
    int passTicks = 1 + (int)(Geom.getMaxDistanceToBot(source, location) / speed);
    return passTicks - (int)(time - fireTime);
  }

  // https://robowiki.net/wiki/Waves/Precise_Intersection
  // various early exits for efficiency
  public Foam getFoam(PredictState state) {
    double radius = (state.gameTime - fireTime) * speed;
    double nextRadius = radius + speed;
    double distance = state.location.distance(source);

    if (radius <= 0) {
      return new Foam(MIDAIR, null);
    }
    if (nextRadius < distance - Geom.MAX_BOT_HALF_WIDTH) {
      return new Foam(MIDAIR, null);
    }
    if (radius > distance + Geom.MAX_BOT_HALF_WIDTH) {
      return new Foam(PASSED, null);
    }

    double[] radiusSq_nextRadiusSq = new double[] {
        MathUtils.sqr(radius), MathUtils.sqr(nextRadius)};
    double[] yBounds = new double[] {state.location.y - 18, state.location.y + 18};
    double[] xBounds = new double[] {state.location.x - 18, state.location.x + 18};
    ArrayList<Point2D.Double> intercepts = new ArrayList<>();
    double maxCornerDistanceSq = 0;

    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        Point2D.Double corner = new Point2D.Double(xBounds[i], yBounds[j]);
        double cornerDistance = corner.distanceSq(source);
        if (cornerDistance >= radiusSq_nextRadiusSq[0] &&
            cornerDistance <= radiusSq_nextRadiusSq[1]) {
          intercepts.add(corner);
        }
        if (cornerDistance > maxCornerDistanceSq) {
          maxCornerDistanceSq = cornerDistance;
        }
      }
    }

    if (radius > Math.sqrt(maxCornerDistanceSq)) {
      return new Foam(PASSED, null);
    }

    for (int beforeTick = 0; beforeTick < 2; beforeTick++) {
      double radiusSq = radiusSq_nextRadiusSq[beforeTick];
      for (double y : yBounds) {
        for (double x: Geom.getXIntercepts(source, radiusSq, y)) {
          if (MathUtils.inBounds(x, xBounds)) {
            intercepts.add(new Point2D.Double(x, y));
          }
        }
      }
      for (double x : xBounds) {
        for (double y: Geom.getYIntercepts(source, radiusSq, x)) {
          if (MathUtils.inBounds(y, yBounds)) {
            intercepts.add(new Point2D.Double(x, y));
          }
        }
      }
    }

    if (intercepts.isEmpty()) {
      return new Foam(MIDAIR, null);
    }

    Range range = new Range();
    for (Point2D.Double intercept : intercepts) {
      range.update(Geom.offset(source, intercept, absoluteBearing));
    }
    return new Foam(nextRadius > Math.sqrt(maxCornerDistanceSq) ?
        WILL_PASS_THIS_TICK: BREAKING, range);
  }

  public static class Foam {
    public final int status;
    public final Range hitOffsetRange;

    public Foam(int status, Range hitOffsetRange) {
      this.status = status;
      this.hitOffsetRange = hitOffsetRange;
    }
  }
}
