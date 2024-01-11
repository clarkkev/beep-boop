package kc.mega.utils;

/** Collections of helpful functions. */
public class MathUtils {
  public static double clip(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }

  public static int clip(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  public static int nonzeroSign(double v) {
    return v > 0 ? 1 : -1;
  }

  public static double sqr(double v) {
    return v * v;
  }

  public static boolean inBounds(double v, double lo, double hi){
    return v >= lo && v <= hi;
  }

  public static boolean inBounds(double v, double[] bounds){
    return inBounds(v, bounds[0], bounds[1]);
  }

  public static void divide(double[] x, double divisor) {
    for (int i = 0; i < x.length; i++) {
      x[i] /= divisor;
    }
  }

  public static void normalize(double[] x) {
    double total = 0;
    for (int i = 0; i < x.length; i++) {
      total += x[i];
    }
    if (total == 0) {
      return;
    }
    divide(x, total);
  }

  public static void softmax(double[] logits, double maxLogit) {
    double total = 0;
    for (int i = 0; i < logits.length; i++) {
      double weight = Math.exp(logits[i] - maxLogit);
      logits[i] = weight;
      total += weight;
    }
    divide(logits, total);
  }

  public static double[] addArrays(double[] x, double[] y) {
    double[] result = new double[x.length];
    for (int i = 0; i < x.length; i++) {
      result[i] = x[i] + y[i];
    }
    return result;
  }
}
