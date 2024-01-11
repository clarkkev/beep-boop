package kc.mega.shared;

import kc.mega.aim.wave.AimWaves;
import kc.mega.move.wave.MovementWave;
import kc.mega.move.wave.MovementWaves;
import kc.mega.wave.Bullet;
import kc.mega.wave.Wave;


/** Stores waves and tracks bullet shadows (https://robowiki.net/wiki/Bullet_Shadow). */
public class WaveManager {
  public final AimWaves aimWaves = new AimWaves();
  public final MovementWaves movementWaves = new MovementWaves();

  public void onRoundStart() {
    aimWaves.clear();
    movementWaves.clear();
  }

  public void onBulletHitBullet() {
    for (MovementWave w : movementWaves.getBulletWaves()) {
      w.shadows = new double[Wave.BINS.nBins];
      movementWaves.updatedShadowWaves.add(w);
      for (Bullet b : aimWaves.getBullets()) {
        movementWaves.addShadows(w, b);
      }
    }
  }

  public void onFire(Bullet b) {
    for (MovementWave w : movementWaves.getBulletWaves()) {
      movementWaves.addShadows(w, b);
    }
  }

  public void onEnemyFire(MovementWave w) {
    for (Bullet b : aimWaves.getBullets()) {
      movementWaves.addShadows(w, b);
    }
  }
}
