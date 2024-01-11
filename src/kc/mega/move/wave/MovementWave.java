package kc.mega.move.wave;

import kc.mega.utils.Geom;
import kc.mega.utils.Painter;
import kc.mega.wave.WaveWithFeatures;

import java.awt.Color;

/** One of the opponent's waves, used for wave surfing. */
public class MovementWave extends WaveWithFeatures {
  public final boolean isVirtual;
  public final boolean isSimulated;

  public static class Builder extends WaveWithFeatures.Builder {
    public boolean isVirtual;
    public boolean isSimulated;
    public Builder isVirtual(boolean val) {isVirtual = val; return this;}
    public Builder isSimulated(boolean val) {isSimulated = val; return this;}
    @Override
    public MovementWave build() {return new MovementWave(this);}
  }

  private MovementWave(Builder builder) {
    super(builder);
    this.isVirtual = builder.isVirtual;
    this.isSimulated = builder.isSimulated;
  }

  public void paintDangers(double[] dangers) {
    double maxDanger = 0;
    for (int i = 0; i < dangers.length; i++) {
      maxDanger = Math.max(dangers[i], maxDanger);
    }
    for (int i = 0; i < dangers.length; i++) {
      double GF = (i + 0.5) * 2 / dangers.length - 1;
      int red = (int)(255 * dangers[i] / maxDanger);
      int green = shadows == null ? 0 : (int)(255 * shadows[i]);
      if (red > 0 || green > 0) {
        Painter.THIS_TICK.addPoint(new Color(red, green, 0), Geom.project(
            source, absoluteBearing + getOffset(GF), radius - 7));
      }
    }
  }
}
