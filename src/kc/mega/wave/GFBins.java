package kc.mega.wave;

import kc.mega.utils.Range;

/** Bins for holding GuessFactor visit count stats (https://robowiki.net/wiki/Visit_Count_Stats). */
public class GFBins {
  public final int nBins;
  public final double binWidth;
  public final double[] lowerGF;
  public final double[] upperGF;
  public final double[] midPoint;

  public GFBins(int nBins) {
    this.nBins = nBins;
    binWidth = 2.0 / nBins;
    lowerGF = new double[nBins];
    upperGF = new double[nBins];
    midPoint = new double[nBins];
    for (int i = 0; i < nBins; i++) {
      lowerGF[i] = (i * binWidth) - 1;
      upperGF[i] = ((i + 1) * binWidth) - 1;
      midPoint[i] = ((i + 0.5) * binWidth) - 1;
    }
  }

  public int getBin(double GF) {
    return (int)Math.round(((GF + 1) / binWidth) - 0.5);
  }

  public double binWeight(Range GFRange, int bin) {
    double lowPoint = Math.max(lowerGF[bin], GFRange.start);
    double highPoint = Math.min(upperGF[bin], GFRange.end);
    return Math.max(0, highPoint - lowPoint) / binWidth;
  }

  public int expKernelWidth(double lambda) {
    // how many bins until the kernel gives less than 2% weight
    return (int)Math.round(-Math.log(0.02) / (lambda * binWidth));
  }

  public void updateBinsWithExpKernel(double[] bins, double GF, double lambda, double weight) {
    int bin = getBin(GF);
    int maxBinDiff = expKernelWidth(lambda);
    for (int i = Math.max(0, bin - maxBinDiff);
        i <= Math.min(nBins - 1, bin + maxBinDiff); i++) {
      bins[i] += weight * Math.exp(Math.abs(midPoint[i] - GF) * -lambda);
    }
  }
}
