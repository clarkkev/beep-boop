package kc.mega.wave;

import java.awt.geom.Point2D;

public class Bullet {
  public final Point2D.Double source;
  public final double heading;
  public final long fireTime;
  public final double dx, dy;

  public Bullet(Point2D.Double source, double heading, double speed, long fireTime) {
    this.source = source;
    this.heading = heading;
    this.fireTime = fireTime;
    dx = Math.sin(heading) * speed;
    dy = Math.cos(heading) * speed;
  }

  public Bullet(Wave w, double heading) {
    this(w.source, heading, w.speed, w.fireTime);
  }

  public Point2D.Double getLocation(long gameTime) {
    return new Point2D.Double(source.x + dx * (gameTime - fireTime),
                              source.y + dy * (gameTime - fireTime));
  }

  public Point2D.Double getMidpoint(long gameTime) {
    return new Point2D.Double(source.x + dx * (0.5 + gameTime - fireTime),
                              source.y + dy * (0.5 + gameTime - fireTime));
  }
}