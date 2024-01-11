package kc.mega.move.wave;

import kc.mega.game.PredictState;
import kc.mega.wave.Bullet;
import kc.mega.wave.Wave;
import kc.mega.wave.Waves;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The opponent's active waves. */
public class MovementWaves extends Waves<MovementWave> {
  public final Set<MovementWave> updatedShadowWaves = new HashSet<>();

  public List<MovementWave> getSurfableWaves(PredictState myState) {
    return waves.stream().filter(w -> w.hasBullet && !w.didHit && waveStatuses.containsKey(w) &&
        waveStatuses.get(w) <= Wave.BREAKING).sorted(
        // smaller impact time and larger power comes first
        (w1, w2) -> (int)Math.signum(
            w1.ticksUntilBreak - w2.ticksUntilBreak +
            (w2.power - w1.power) / 3.01)
        ).collect(Collectors.toList());
  }

  public boolean noDangerousWavesExist(PredictState myState) {
    return getSurfableWaves(myState).stream().allMatch(w -> w.isVirtual);
  }

  public void addShadows(MovementWave w, Bullet b) {
    if (w.addBulletShadows(b)) {
      updatedShadowWaves.add(w);
    }
  }
}