package kc.mega.aim.wave;

import kc.mega.wave.Bullet;
import kc.mega.wave.WaveWithFeatures;

/** One of our waves, used for collecting aiming data. */
public class AimWave extends WaveWithFeatures {
  public final Bullet bullet;

  public static class Builder extends WaveWithFeatures.Builder {
    private double gunHeading;
    public Builder gunHeading(double val) {gunHeading = val; return this;}
    @Override
    public AimWave build() {return new AimWave(this);}
  }

  private AimWave(Builder builder) {
    super(builder);
    bullet = hasBullet ? new Bullet(this, builder.gunHeading) : null;
  }
}
