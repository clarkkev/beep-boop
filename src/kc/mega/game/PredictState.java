package kc.mega.game;

import kc.mega.utils.Geom;

import java.awt.geom.Point2D;

import jk.math.FastTrig;

/** Represents the position and movement of a bot for use in precise prediction. */
public class PredictState {
  public final Point2D.Double location;
  public final double heading;
  public final double velocity;
  public final long gameTime;

  public PredictState(Point2D.Double location, double heading, double velocity, long gameTime) {
    this.location = location;
    this.heading = heading;
    this.velocity = velocity;
    this.gameTime = gameTime;
  }

  public PredictState getNextState(double turn) {
    return getNextState(velocity, turn);
  }

  public PredictState getNextState(int direction, double turn) {
    return getNextState(Physics.nextVelocity(velocity, direction), turn);
  }

  private PredictState getNextState(double nextVelocity, double turn) {
    double nextHeading = heading + Physics.turnIncrement(turn, velocity);
    return new PredictState(Geom.project(location, nextHeading, nextVelocity),
        nextHeading, nextVelocity, gameTime + 1);
  }

  public PredictState predictNextState(PredictState previous) {
    double turn = FastTrig.normalRelativeAngle(heading - previous.heading);
    if (Math.abs(velocity - previous.velocity) < 1e-4) {
      return getNextState(turn);
    } else {
      return getNextState((int)Math.signum(velocity - previous.velocity), turn);
    }
  }

  public double distance(PredictState state) {
    return location.distance(state.location);
  }

  public double absoluteBearing(PredictState state) {
    return Geom.absoluteBearing(location, state.location);
  }

  public double offset(PredictState state, double bearing) {
    return Geom.offset(location, state.location, bearing);
  }

  public boolean collidesWith(PredictState other) {
    if (location.x - 18.01 < other.location.x + 18.01 &&
        location.x + 18.01 > other.location.x - 18.01 &&
        location.y - 18.01 < other.location.y + 18.01 &&
        location.y + 18.01 > other.location.y - 18.01) {
      double bearing = FastTrig.normalRelativeAngle(absoluteBearing(other) - heading);
      return ((velocity > 0 && bearing > -Math.PI / 2 && bearing < Math.PI / 2) ||
              (velocity < 0 && (bearing < -Math.PI / 2 || bearing > Math.PI / 2)));
    }
    return false;
  }
}
