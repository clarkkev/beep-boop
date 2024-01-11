package kc.mega.game;

import java.awt.geom.Point2D;


/** Represents the game state for a bot. */
public class BotState extends PredictState {
  public long lastFireTime;
  public int shotsFired;
  public double gunHeat;
  public double energy;

  public BotState(Point2D.Double location, double heading, double velocity, double energy,
                   double gunHeat, int shotsFired, long lastFireTime, long gameTime) {
    super(location, heading, velocity, gameTime);
    this.energy = energy;
    this.gunHeat = gunHeat;
    this.shotsFired = shotsFired;
    this.lastFireTime = lastFireTime;
  }

  public BotState(PredictState state, BotState currentState) {
    super(state.location, state.heading, state.velocity, state.gameTime);
    shotsFired = currentState.shotsFired;
  }

  @Override
  public BotState getNextState(int direction, double turn) {
    return getNextState(super.getNextState(direction, turn));
  }

  @Override
  public BotState getNextState(double turn) {
    return getNextState(super.getNextState(turn));
  }

  @Override
  public BotState predictNextState(PredictState previous) {
    return getNextState(super.predictNextState(previous));
  }

  private BotState getNextState(PredictState predicted) {
    return new BotState(
        predicted.location, predicted.heading, predicted.velocity,
        energy, gunHeat - BattleField.INSTANCE.getGunCoolingRate(),
        shotsFired, lastFireTime, gameTime + 1);
  }

  public PredictState asPredictState() {
    return new PredictState(location, heading, velocity, gameTime);
  }

  public void updateFromFire(double power) {
    gunHeat = Physics.gunHeat(power);
    energy -= power;
    lastFireTime = gameTime;
    shotsFired += 1;
  }

  public void updateFromBulletHit(double power) {
    energy += power * 3;
  }

  public void updateFromHitByBullet(double power) {
    energy = Math.max(energy - Physics.bulletDamage(power), 0.0);
  }
}
