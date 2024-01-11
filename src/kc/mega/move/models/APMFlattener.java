package kc.mega.move.models;

import kc.mega.game.GameState;
import kc.mega.wave.GFBins;
import kc.mega.wave.WaveWithFeatures;

/** Simulates simple https://robowiki.net/wiki/Symbolic_Pattern_Matching targeting. */
public class APMFlattener extends DangerModel {
  private static final int MAX_HISTORY_SIZE = 30000;
  private static final int MIN_TICKS_AHEAD = 64;

  private String history = "";
  private long gameTime;

  private final GFBins bins;

  public APMFlattener(GFBins bins) {
    super("APMFlattener");
    this.bins = bins;
  }

  @Override
  public void onTurn(GameState gs) {
    gameTime = gs.gameTime;
    history = WaveWithFeatures.getPMSymbol(gs.enemyState, gs.myState) + history;
    if (history.length() > MAX_HISTORY_SIZE) {
      history = history.substring(0, MAX_HISTORY_SIZE);
    }
  }

  @Override
  public double[] getDangers(WaveWithFeatures w) {
    double[] dangers = new double[bins.nBins];
    if (history.length() <= MIN_TICKS_AHEAD) {
      return dangers;
    }
    int maxMatches = Math.min(10, history.length() / 10);
    int matches = 0;
    int matchLen = Math.min(40, w.pattern.length());
    while (matches < maxMatches) {
      String pattern = w.pattern.substring(0, matchLen);
      int matchPos = MIN_TICKS_AHEAD + (int)(gameTime - w.fireTime);
      while (matches < maxMatches && (matchPos = history.indexOf(pattern, matchPos)) > 0) {
        double offset = 0;
        double distanceRemaining = w.distance;
        int i = matchPos;
        do {
          offset += ((short)history.charAt(--i)) / w.distance;
        } while ((distanceRemaining -= w.speed) > 0 && i > 0);
        matches++;
        matchPos++;
        dangers[bins.getBin(w.getGF(w.prevAbsoluteBearing + offset - w.absoluteBearing))] +=
            matchLen / matches;
      };
      matchLen--;
    }
    return dangers;
  }
}
