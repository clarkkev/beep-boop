package kc.mega.aim.wave;

import kc.mega.wave.Bullet;
import kc.mega.wave.Waves;

import java.util.List;
import java.util.stream.Collectors;

/** Our bot's active waves. */
public class AimWaves extends Waves<AimWave> {
  public List<Bullet> getBullets() {
    return getBulletWaves().stream().map(w -> w.bullet).collect(Collectors.toList());
  }
}