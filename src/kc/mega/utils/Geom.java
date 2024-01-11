package kc.mega.utils;

import kc.mega.game.BattleField;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import jk.math.FastTrig;
import robocode.util.Utils;

/** Geometry-related utilities. */
public class Geom {
  public static final double MAX_BOT_HALF_WIDTH = Math.sqrt(2) * 18.001;
  public static final double MAX_BOT_WIDTH = 2 * MAX_BOT_HALF_WIDTH;

  public static double maxBotAngle(double distance) {
    return FastTrig.atan(MAX_BOT_WIDTH / distance);
  }

  public static double maxHalfBotAngle(double distance) {
    return FastTrig.atan(MAX_BOT_HALF_WIDTH / distance);
  }

  public static Point2D.Double project(Point2D source, double heading, double distance) {
    return new Point2D.Double(source.getX() + (FastTrig.sin(heading) * distance),
                              source.getY() + (FastTrig.cos(heading) * distance));
  }

  public static double absoluteBearing(Point2D source, Point2D target) {
    return FastTrig.atan2(target.getX() - source.getX(), target.getY() - source.getY());
  }

  public static double offset(Point2D source, Point2D target, double bearing) {
    return FastTrig.normalRelativeAngle(absoluteBearing(source, target) - bearing);
  }

  public static int orbitDirection(double absoluteBearing, double heading, int moveDirection) {
    return MathUtils.nonzeroSign(FastTrig.sin(heading - absoluteBearing) * moveDirection);
  }

  public static Point2D.Double projectPrecise(Point2D source, double heading, double distance) {
    return new Point2D.Double(source.getX() + (Math.sin(heading) * distance),
                              source.getY() + (Math.cos(heading) * distance));
  }

  public static double absoluteBearingPrecise(Point2D source, Point2D target) {
    return Math.atan2(target.getX() - source.getX(), target.getY() - source.getY());
  }

  public static double offsetPrecise(Point2D source, Point2D target, double bearing) {
    return Utils.normalRelativeAngle(absoluteBearingPrecise(source, target) - bearing);
  }

  public static double getMinDistanceToBot(Point2D.Double source, Point2D.Double botLocation) {
    double minDistance = Math.min(
        Math.min(
          source.distance(new Point2D.Double(botLocation.x - 18, botLocation.y - 18)),
          source.distance(new Point2D.Double(botLocation.x - 18, botLocation.y + 18))),
        Math.min(
          source.distance(new Point2D.Double(botLocation.x + 18, botLocation.y - 18)),
          source.distance(new Point2D.Double(botLocation.x + 18, botLocation.y + 18))));
    if (Math.abs(source.x - botLocation.x) < 18.0) {
      minDistance = Math.min(minDistance, Math.min(Math.abs(source.y - botLocation.y - 18),
                                                   Math.abs(source.y - botLocation.y + 18)));
    }
    if (Math.abs(source.y - botLocation.y) < 18.0) {
      minDistance = Math.min(minDistance, Math.min(Math.abs(source.x - botLocation.x - 18),
                                                   Math.abs(source.x - botLocation.x + 18)));
    }
    return minDistance;
  }

  public static double getMaxDistanceToBot(Point2D.Double source, Point2D.Double botLocation) {
    return Math.max(
        Math.max(
          source.distance(new Point2D.Double(botLocation.x - 18, botLocation.y - 18)),
          source.distance(new Point2D.Double(botLocation.x - 18, botLocation.y + 18))),
        Math.max(
          source.distance(new Point2D.Double(botLocation.x + 18, botLocation.y - 18)),
          source.distance(new Point2D.Double(botLocation.x + 18, botLocation.y + 18))));
  }

  public static List<Point2D.Double> getBotCornerPoints(List<Point2D.Double> points) {
    List<Point2D.Double> newPoints = new ArrayList<>();
    for (Point2D.Double point : points) {
      newPoints.add(new Point2D.Double(point.x + 18, point.y + 18));
      newPoints.add(new Point2D.Double(point.x + 18, point.y - 18));
      newPoints.add(new Point2D.Double(point.x - 18, point.y + 18));
      newPoints.add(new Point2D.Double(point.x - 18, point.y - 18));
    }
    return newPoints;
  }

  public static double[] getXIntercepts(Point2D.Double center, double radiusSquared, double y) {
    double root = radiusSquared - MathUtils.sqr(y - center.y);
    if(root < 0) {
      return new double[] {};
    } else {
      root = Math.sqrt(root);
      return new double[] {center.x + root, center.x - root};
    }
  }

  public static double[] getYIntercepts(Point2D.Double center, double radiusSquared, double x) {
    double root = radiusSquared - MathUtils.sqr(x - center.x);
    if(root < 0) {
      return new double[] {};
    } else {
      root = Math.sqrt(root);
      return new double[] {center.y + root, center.y - root};
    }
  }

  public static List<Point2D.Double> getXInterceptsInRange(
      Point2D.Double center, double radiusSquared, double y, double xMin, double xMax) {
    List<Point2D.Double> intercepts = new ArrayList<>();
    for (double x : getXIntercepts(center, radiusSquared, y)) {
      if (MathUtils.inBounds(x, xMin, xMax)) {
        intercepts.add(new Point2D.Double(x, y));
      }
    }
    return intercepts;
  }

  public static List<Point2D.Double> getYInterceptsInRange(
      Point2D.Double center, double radiusSquared, double x, double yMin, double yMax) {
    List<Point2D.Double> intercepts = new ArrayList<>();
    for (double y : getYIntercepts(center, radiusSquared, x)) {
      if (MathUtils.inBounds(y, yMin, yMax)) {
        intercepts.add(new Point2D.Double(x, y));
      }
    }
    return intercepts;
  }

  public static List<Point2D.Double> circleRectangleIntercepts(
      Circle circle, Rectangle2D.Double rectangle) {
    List<Point2D.Double> intercepts = new ArrayList<>();
    double radiusSquared = MathUtils.sqr(circle.radius);
    double xMin = rectangle.x;
    double xMax = rectangle.x + rectangle.width;
    double yMin = rectangle.y;
    double yMax = rectangle.y + rectangle.height;
    intercepts.addAll(getXInterceptsInRange(circle.center, radiusSquared, yMin, xMin, xMax));
    intercepts.addAll(getXInterceptsInRange(circle.center, radiusSquared, yMax, xMin, xMax));
    intercepts.addAll(getYInterceptsInRange(circle.center, radiusSquared, xMin, yMin, yMax));
    intercepts.addAll(getYInterceptsInRange(circle.center, radiusSquared, xMax, yMin, yMax));
    return intercepts;
  }

  public static List<Point2D.Double> getTangentPoints(Point2D.Double source, Circle circle) {
    List<Point2D.Double> points = new ArrayList<>();
    double distance = source.distance(circle.center);
    if (circle.radius > distance) {
      return points;
    }
    double absoluteBearing = absoluteBearing(source, circle.center);
    double tangentAngle = FastTrig.asin(circle.radius / distance);
    double tangentLength = Math.sqrt(MathUtils.sqr(distance) - MathUtils.sqr(circle.radius));
    points.add(project(source, absoluteBearing - tangentAngle, tangentLength));
    points.add(project(source, absoluteBearing + tangentAngle, tangentLength));
    return points;
  }

  // https://stackoverflow.com/questions/1073336/circle-line-segment-collision-detection-algorithm
  public static Point2D.Double circleLineSegIntercept(
      Point2D.Double segmentStart, Point2D.Double segmentEnd,
      Point2D.Double center, double radius) {
    double dx = segmentEnd.x - segmentStart.x;
    double dy = segmentEnd.y - segmentStart.y;
    double fx = segmentStart.x - center.x;
    double fy = segmentStart.y - center.y;
    double a = dx * dx + dy * dy;
    double b = 2 * (dx * fx + dy * fy);
    double c = fx * fx + fy * fy - radius * radius;
    double discriminant = b * b - 4 * a * c;
    if (discriminant < 0) {
      return null;
    }
    discriminant = Math.sqrt(discriminant);
    double t1 = (-b - discriminant) / (2 * a);
    if (0 <= t1 && t1 <= 1) {
      return new Point2D.Double(segmentStart.x + dx * t1, segmentStart.y + dy * t1);
    }
    double t2 = (-b + discriminant) / (2 * a);
    if (0 <= t2 && t2 <= 1) {
      return new Point2D.Double(segmentStart.x + dx * t2, segmentStart.y + dy * t2);
    }
    return null;
  }

  public static class Circle {
    public final Point2D.Double center;
    public final double radius;

    public Circle(Point2D.Double center, double radius) {
      this.center = center;
      this.radius = radius;
    }
  }

  public static double simpleMaxEscapeAngle(double speed) {
    return FastTrig.asin(8 / speed);
  }

  // https://robowiki.net/wiki/Maximum_Escape_Angle/Precise_Positional/Non-Iterative
  public static double[] inFieldMaxEscapeAngles(
      Point2D.Double source, Point2D.Double target, double bulletSpeed) {
    Circle escapeCircle = getEscapeCircle(source, target, bulletSpeed, 0);
    List<Point2D.Double> testPoints = new ArrayList<>();
    for (Point2D.Double point : getTangentPoints(source, escapeCircle)) {
      if (BattleField.INSTANCE.getFieldRectangle().contains(point)) {
        testPoints.add(point);
      }
    }
    testPoints.addAll(circleRectangleIntercepts(
        escapeCircle, BattleField.INSTANCE.getFieldRectangle()));
    Range range = new Range();
    double absoluteBearing = absoluteBearing(source, target);
    for (Point2D.Double point : testPoints) {
      range.update(offset(source, point, absoluteBearing));
    }
    return new double[] {Math.max(Math.abs(range.start), 0.001), Math.max(range.end, 0.001)};
  }

  public static double wallRestriction(
      Point2D.Double source, Point2D.Double target, double bulletSpeed) {
    double[] mae = inFieldMaxEscapeAngles(source, target, bulletSpeed);
    return (mae[0] + mae[1]) / (2 * simpleMaxEscapeAngle(bulletSpeed));
  }

  public static Circle getEscapeCircle(Point2D.Double source, Point2D.Double botLocation,
      double bulletSpeed, double addedDistance) {
    double absoluteBearing = absoluteBearing(source, botLocation);
    double distance = source.distance(botLocation) + addedDistance;
    double r1 = 8 * distance / (bulletSpeed + 8);
    double r2 = 8 * distance / (bulletSpeed - 8);
    double radius = (r1 + r2) / 2;
    Point2D.Double center = project(botLocation, absoluteBearing, radius - r1);
    return new Circle(center, radius);
  }
}
