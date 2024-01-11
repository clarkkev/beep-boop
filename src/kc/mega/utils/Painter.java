package kc.mega.utils;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Helper for painting graphics. */
public enum Painter {
  THIS_TICK, PERMANENT, CUSTOM;
  public static final Color TRANSLUCENT_GREEN = new Color(0, 255, 0, 150);
  public static final Color TRANSLUCENT_RED = new Color(255, 0, 0, 150);
  public static boolean active = true;

  private List<Color> colors = new ArrayList<>();
  private List<Shape> toDraw = new ArrayList<>();
  private List<Boolean> doFill = new ArrayList<>();

  public void clear() {
    colors.clear();
    toDraw.clear();
    doFill.clear();
  }

  public void addShape(Color c, Shape s, boolean fill) {
    colors.add(c);
    toDraw.add(s);
    doFill.add(fill);
  }

  public void addBot(Color c, Point2D.Double location) {
    addShape(c, new Rectangle2D.Double(
        location.x - 18, location.y - 18, 36, 36), false);
  }

  public void addCircle(Color c, Point2D.Double center, double r) {
    addCircle(c, center, r, false);
  }

  public void addCircle(Color c, Point2D.Double center, double r, boolean fill) {
    addShape(c, new Ellipse2D.Double(
        center.x - r, center.y - r, 2 * r, 2 * r), fill);
  }

  public void addPoint(Color c, Point2D.Double location) {
    addPoint(c, location, 1.5);
  }

  public void addPoint(Color c, Point2D.Double location, double size) {
    addCircle(c, location, size, true);
  }

  public void addLine(Color c, Point2D.Double source, Point2D.Double target) {
    addShape(c, new Line2D.Double(source.x, source.y, target.x, target.y), false);
  }

  public void addLine(Color c, Point2D.Double source, double heading, double d) {
    Point2D.Double endPoint = Geom.project(source, heading, d);
    addShape(c, new Line2D.Double(source.x, source.y, endPoint.x, endPoint.y), false);
  }

  public void paint(java.awt.Graphics2D g) {
    for (int i = 0; i < colors.size(); i++) {
      g.setColor(colors.get(i));
      if (doFill.get(i)) {
        g.fill(toDraw.get(i));
      } else {
        g.draw(toDraw.get(i));
      }
    }
  }

  public static void paint(java.awt.Graphics2D g, boolean shouldBeActive) {
    Painter.active = shouldBeActive;
    if (shouldBeActive) {
      THIS_TICK.paint(g);
      CUSTOM.paint(g);
      PERMANENT.paint(g);
    }
    THIS_TICK.clear();
  }
}
