package kc.mega.wave;

import kc.mega.game.BotState;
import kc.mega.game.PredictState;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Stores the set of active waves created by a single bot. */
public abstract class Waves<W extends Wave> {
  protected final List<W> waves = new ArrayList<>();
  protected final Map<W, Integer> waveStatuses = new HashMap<>();

  public void clear() {
    waves.clear();
  }

  public void add(W w) {
    waves.add(w);
  }

  public void remove(W w) {
    waves.remove(w);
  }

  public List<W> get() {
    return waves;
  }

  public List<W> getBulletWaves() {
    return waves.stream().filter(w -> w.hasBullet).collect(Collectors.toList());
  }

  public List<W> update(BotState enemyState) {
    waveStatuses.clear();
    List<W> passedWaves = new ArrayList<>();
    for (W w : new ArrayList<>(waves)) {
      int waveStatus = w.update(enemyState);
      waveStatuses.put(w, waveStatus);
      if (waveStatus == Wave.PASSED) {
        remove(w);
        if (w.hitOffset < 100) {
          passedWaves.add(w);
        } else {
          System.out.println("Wave passed with no data");
        }
      }
    }
    return passedWaves;
  }

  public W closestWave(PredictState enemyState, boolean requireBullet) {
    Optional<W> option = waves.stream()
        .filter(w -> w.getTicksUntilPasses(enemyState) > 0)
        .filter(w -> requireBullet ? w.hasBullet : true)
        .min((w1, w2) -> Math.abs(w1.getTicksUntilBreak(enemyState)) -
            Math.abs(w2.getTicksUntilBreak(enemyState)));
    return option.isPresent() ? option.get() : null;
  }

  public double getCurrentGF(PredictState enemyState) {
    W w = closestWave(enemyState, false);
    return w == null ? 0 : w.getGF(enemyState.location);
  }

  public W getHitWave(double power) {
    for (W w : getBulletWaves()) {
      if (waveStatuses.containsKey(w) && waveStatuses.get(w) > Wave.MIDAIR &&
          Math.abs(w.power - power) < 1e-4) {
        w.didHit = true;
        return w;
      }
    }
    System.out.println("Couldn't find bullet hit wave!");
    return null;
  }

  public W getBulletHitBulletWave(Point2D.Double hitLocation, double power, long gameTime) {
    for (W w : getBulletWaves()) {
      double distance = w.source.distance(hitLocation);
      if (Math.abs(w.power - power) < 1e-4 &&
          (Math.abs(distance - w.speed * (gameTime - w.fireTime - 1)) < 0.01 ||
           Math.abs(distance - w.speed * (gameTime - w.fireTime - 2)) < 0.01)) {
        w.hasBullet = false;
        w.didCollide = true;
        return w;
      }
    }
    System.out.println("Couldn't find bullet collision wave!");
    return null;
  }
}