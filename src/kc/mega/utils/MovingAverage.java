package kc.mega.utils;

/** Tracks an exponential moving average. */
public class MovingAverage {
  private final double rate;
  private int n;
  private double value;

  public MovingAverage(double rate) {
    this.rate = rate;
  }

  public void update(double x) {
    n++;
    value = (1 - rate) * x + (rate * value);
  }

  public double get() {
    return value / (1 - Math.pow(rate, Math.max(1, n)));
  }
}
