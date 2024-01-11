package kc.mega.move;

import kc.mega.game.BattleField;
import kc.mega.game.BotState;
import kc.mega.game.GameState;
import kc.mega.game.Physics;
import kc.mega.game.PredictState;
import kc.mega.move.wave.MovementWave;
import kc.mega.shared.Strategy;
import kc.mega.utils.Geom;
import kc.mega.utils.MathUtils;
import kc.mega.utils.Range;
import kc.mega.wave.Wave;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jk.math.FastTrig;

/** Simulates what will happen when executing a movement plan. */
public class Simulation {
  public final GameState gs;
  public final Strategy strategy;
  public final int antiRamEvadeSide;
  public final List<PredictState> states;
  public final List<MovementWave> waves;
  public final Map<MovementWave, Range> visitOffsetRanges;
  public final List<Integer> path;
  public final List<Double> travelDistances;
  public final int startTick;
  public final int pathStart;
  public final double enemyTurn;

  public boolean enemyFired;
  public PredictState predictedEnemyState;
  public List<Integer> extension;

  public Simulation(Strategy strategy, List<MovementWave> waves) {
    gs = strategy.gs;
    this.strategy = strategy;
    this.waves = new ArrayList<>(waves);
    antiRamEvadeSide = 1;
    startTick = 0;
    pathStart = 0;
    states = new ArrayList<>();
    states.add(gs.myState.asPredictState());
    travelDistances = new ArrayList<>();
    travelDistances.add(0.0);
    visitOffsetRanges = new HashMap<>();
    path = new ArrayList<>();
    predictedEnemyState = gs.enemyState.asPredictState();
    enemyTurn = FastTrig.normalRelativeAngle(gs.enemyState.heading - gs.getEnemyState(-1).heading);
  }

  public Simulation(Simulation parent, List<Integer> extension, int antiRamEvadeSide) {
    this.antiRamEvadeSide = antiRamEvadeSide;
    gs = parent.gs;
    strategy = parent.strategy;
    waves = new ArrayList<>(parent.waves);
    states = new ArrayList<>(parent.states);
    travelDistances = new ArrayList<>(parent.travelDistances);
    visitOffsetRanges = new HashMap<>();
    for (Map.Entry<MovementWave, Range> e : parent.visitOffsetRanges.entrySet()) {
      this.visitOffsetRanges.put(e.getKey(), new Range(e.getValue()));
    }
    path = new ArrayList<>(parent.path);
    path.addAll(extension);
    startTick = parent.states.size() - 1;
    pathStart = parent.path.size();
    enemyFired = parent.enemyFired;
    enemyTurn = parent.enemyTurn;
    predictedEnemyState = parent.predictedEnemyState;
  }

  public void simulate(MovementWave surfWave, boolean precise, boolean simulateFutureWaves) {
    PredictState state = states.get(states.size() - 1);
    int tick = 0;
    int direction = 0;
    int nonzeroDirection = 0;
    double distanceTraveled = 0;
    boolean surfWavePassed = surfWave == null;

    boolean predictRammer = strategy.antiRam && gs.enemyIsAlive && precise;
    boolean ICollidedLastTick = gs.lastCollideTime == gs.gameTime;
    boolean enemyCollidedLastTick = gs.lastEnemyCollideTime == gs.gameTime;

    List<BotState> myHistory = null;
    List<BotState> enemyHistory = null;
    if (simulateFutureWaves) {
       myHistory = new ArrayList<>(gs.myHistory);
       enemyHistory = new ArrayList<>(gs.enemyHistory);
    }

    int noWaveTicks = strategy.ram ? (int)state.distance(predictedEnemyState) / 20 :
      (predictRammer ? 20 : 5);

    while (!surfWavePassed || (tick < noWaveTicks && waves.size() <= (surfWave == null ? 0 : 1))) {
      // get the next direction in the path or extend the path with the current direction
      tick = (int)(state.gameTime - states.get(0).gameTime);
      if (tick < path.size()) {
        direction = path.get(tick);
        if (direction != 0) {
          nonzeroDirection = direction;
        }
      } else {
        path.add(direction);
      }
      if (Math.abs(state.velocity) > 1.99 && state.velocity * direction < 0) {
        // halt when reversing and halting are equivalent (canonical representation)
        path.set(tick, 0);
      }

      // update the enemy's state
      if (predictRammer) {
        PredictState nextEnemyState = simulateRammer(state, predictedEnemyState, enemyCollidedLastTick);
        if (enemyCollidedLastTick = nextEnemyState.collidesWith(state)) {
          predictedEnemyState = new PredictState(
              predictedEnemyState.location, nextEnemyState.heading, 0, nextEnemyState.gameTime);
        } else {
          predictedEnemyState = nextEnemyState;
        }
      } else {
        predictedEnemyState = predictedEnemyState.getNextState(0, enemyTurn);
      }

      // update our state
      double targetHeading = getTargetHeading(state, predictedEnemyState,
          nonzeroDirection == 0 ? MathUtils.nonzeroSign(state.velocity) : nonzeroDirection);
      double turn = ICollidedLastTick ? 0 : FastTrig.normalRelativeAngle(
          targetHeading - state.heading);
      state = state.getNextState(direction, turn);
      state = BattleField.INSTANCE.wallCollisionCheck(state);
      if (ICollidedLastTick = (predictRammer && state.collidesWith(predictedEnemyState))) {
        state = new PredictState(getEndLocation(), state.heading, 0, state.gameTime);
      }
      states.add(state);
      distanceTraveled += state.velocity;
      travelDistances.add(distanceTraveled);

      // against rambots create gun heat waves during surf prediction
      if (simulateFutureWaves && !enemyFired && waves.size() < 2) {
        myHistory.add(0, new BotState(state, gs.myState));
        enemyHistory.add(0, new BotState(predictedEnemyState, gs.enemyState));
        if (gs.enemyState.gunHeat < (tick + 1) * BattleField.INSTANCE.getGunCoolingRate()) {
          enemyFired = true;
          MovementWave w =
              (MovementWave)new MovementWave.Builder()
              .isVirtual(true)
              .isSimulated(true)
              .myHistory(enemyHistory)
              .enemyHistory(myHistory)
              .power(Math.min(gs.enemyState.energy, gs.lastEnemyBulletPower))
              .build();
          w.ticksUntilBreak = w.getTicksUntilBreak(gs.myState);
          waves.add(w);
        }
      }

      // update waves and visit offsets
      if (precise) {
        for (MovementWave w : waves) {
          Wave.Foam foam = w.getFoam(state);
          surfWavePassed |= w == surfWave && foam.status >= Wave.WILL_PASS_THIS_TICK;
          if (foam.status == Wave.BREAKING || foam.status == Wave.WILL_PASS_THIS_TICK) {
            Range offsetRange = visitOffsetRanges.get(w);
            if (offsetRange == null) {
              visitOffsetRanges.put(w, foam.hitOffsetRange);
            } else {
              offsetRange.merge(foam.hitOffsetRange);
            }
          }
        }
      } else {
        surfWavePassed = surfWave.source.distance(state.location) <
            (1 + state.gameTime - surfWave.fireTime) * surfWave.speed;
      }
    }
    extension = path.subList(pathStart, path.size());
  }

  public MovementWave getSurfWave() {
    PredictState state = states.get(states.size() - 1);
    int minTicksUntilPasses = 1000;
    MovementWave surfWave = null;
    for (MovementWave w : waves) {
      int ticksUntilPasses = w.getTicksUntilPasses(state);
      if (ticksUntilPasses > 1 && ticksUntilPasses < minTicksUntilPasses) {
        minTicksUntilPasses = ticksUntilPasses;
        surfWave = w;
      }
    }
    return surfWave;
  }

  public Point2D.Double getEndLocation() {
    return states.get(states.size() - 1).location;
  }

  // controls how to turn the bot based on the game state
  public double getTargetHeading(PredictState state, PredictState enemyState, int direction) {
    double absoluteBearing = enemyState.absoluteBearing(state);
    double distance = state.distance(enemyState);
    double nextVelocity = Physics.nextVelocity(state.velocity, direction);
    direction = nextVelocity == 0 ? direction : (int)Math.signum(nextVelocity);
    int orbitDirection = Geom.orbitDirection(absoluteBearing, state.heading, direction);

    double targetHeading;
    if (strategy.antiRam) {
      // move away from the enemy at an angle
      targetHeading = absoluteBearing + antiRamEvadeSide * Math.max(0.7, 1.5 - distance / 400);
      if (direction == -1) {
        targetHeading += Math.PI;
      }
    } else if (strategy.ram) {
      // move towards where the enemy will be (https://robowiki.net/wiki/Ramming_Movement)
      PredictState enemyFutureState = enemyState;
      for (int i = 0; i < Math.min(10, distance / 20); i++) {
        enemyFutureState = enemyFutureState.getNextState(
            (int)Math.signum(enemyState.velocity), enemyTurn);
      }
      targetHeading = state.absoluteBearing(enemyFutureState);
      if (direction == -1) {
        targetHeading += Math.PI;
      }
      return targetHeading;
    } else {
      // orbit the enemy; angle further away the closer we are
      double attackAngle = -Math.max((waves.isEmpty() ? 1.5 : 1) * (650 - distance) / 650, 0);
      targetHeading = absoluteBearing + orbitDirection * (attackAngle + direction * Math.PI / 2);
    }

    double turn = FastTrig.normalRelativeAngle(targetHeading - state.heading);
    double nextHeading = state.heading + Physics.turnIncrement(turn, state.velocity);
    // https://robowiki.net/wiki/Wall_Smoothing
    if (strategy.walkingStickSmooth) {
      targetHeading = BattleField.INSTANCE.walkingStickSmooth(
          state.location, targetHeading, direction, orbitDirection, 150, 25);
      turn = FastTrig.normalRelativeAngle(targetHeading - state.heading);
      nextHeading = state.heading + Physics.turnIncrement(turn, state.velocity);
    }
    double offset = direction == 1 ? 0 : Math.PI;
    return BattleField.INSTANCE.fancyStickSmooth(FastTrig.normalAbsoluteAngle(nextHeading + offset),
        Math.abs(nextVelocity), state.location.x, state.location.y, orbitDirection) + offset;
  }

  public static PredictState simulateRammer(PredictState myState, PredictState enemyState,
      boolean enemyCollidedLastTick) {
    double ramHeading = enemyState.absoluteBearing(myState);
    ramHeading += myState.velocity * Math.sin(myState.heading - ramHeading) / 15;
    double turn = FastTrig.normalRelativeAngle(ramHeading - enemyState.heading);
    int direction = 1;
    if (turn < -Math.PI / 2) {
      turn += Math.PI;
      direction = -1;
    } else if (turn > Math.PI / 2) {
      turn -= Math.PI;
      direction = -1;
    }
    PredictState nextEnemyState = enemyState.getNextState(
        direction, enemyCollidedLastTick ? 0 : turn);
    return BattleField.INSTANCE.wallCollisionCheck(nextEnemyState);
  }
}