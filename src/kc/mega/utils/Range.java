package kc.mega.utils;

/** Represents a continuous closed interval. */
public class Range {
  public double start;
  public double end;

  public Range() {
    start = 1000;
    end = -1000;
  }

  public Range(Range range) {
    start = range.start;
    end = range.end;
  }

  public Range(double start, double end) {
    this.start = start;
    this.end = end;
  }

  public static Range fromUnsorted(double x1, double x2) {
    return x1 < x2 ? new Range(x1, x2) : new Range(x2, x1);
  }

  public double width() {
    return end - start;
  }

  public double middle() {
    return (start + end) / 2;
  }

  public void update(double x) {
    start = Math.min(x, start);
    end = Math.max(x, end);
  }

  public void merge(Range other) {
    start = Math.min(start, other.start);
    end = Math.max(end, other.end);
  }

  public boolean isEmpty() {
    return start > 999;
  }
}
