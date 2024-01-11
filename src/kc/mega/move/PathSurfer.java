package kc.mega.move;

import kc.mega.game.BattleField;
import kc.mega.game.BotState;
import kc.mega.game.GameState;
import kc.mega.game.Physics;
import kc.mega.move.wave.MovementWave;
import kc.mega.shared.Strategy;
import kc.mega.utils.Geom;
import kc.mega.utils.MathUtils;
import kc.mega.utils.Painter;
import kc.mega.utils.Range;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jk.math.FastTrig;
import robocode.AdvancedRobot;
import robocode.util.Utils;


/** Uses efficient search on up to three waves to decide on a movement plan. */
public class PathSurfer {
  private static final double THIRD_WAVE_DISCOUNT = 0.75;  // 3rd wave predictions are less precise

  public final AdvancedRobot bot;
  public final GameState gs;
  public final Strategy strategy;
  public final DangerEstimator dangerEstimator;
  public final Paths paths;
  public final List<Node> currentPlan = new ArrayList<>();;
  public final VisitRanges visitRanges = new VisitRanges();
  public final VisitRanges planVisitRanges = new VisitRanges();

  private List<MovementWave> waves;
  private double[] minFutureDanger;

  public PathSurfer(AdvancedRobot bot, Strategy strategy, DangerEstimator dangerEstimator) {
    this.bot = bot;
    this.strategy = strategy;
    this.dangerEstimator = dangerEstimator;
    gs = strategy.gs;
    paths = new Paths();
  }

  public void clearState() {
    currentPlan.clear();
    visitRanges.ranges.clear();
    planVisitRanges.ranges.clear();
  }

  public void surf(List<MovementWave> waves) {
    this.waves = waves;

    // update the best plan found last trick, which we reconsider as an option
    if (currentPlan.size() > 0) {
      currentPlan.get(0).s.extension.remove(0);
      if (currentPlan.get(0).s.extension.isEmpty()) {
        currentPlan.remove(0);
      }
    }

    // avoid using A* pruning when there are overlapping waves
    if ((waves.size() >= 2 && waves.get(1).ticksUntilBreak - waves.get(0).ticksUntilBreak <= 3) ||
        (waves.size() >= 3 && waves.get(2).ticksUntilBreak - waves.get(1).ticksUntilBreak <= 3)) {
      minFutureDanger = new double[] {0, 0, 0};
    } else {
      double thirdWaveDanger = visitRanges.getMinDanger(2) * THIRD_WAVE_DISCOUNT;
      minFutureDanger = new double[] {
          thirdWaveDanger + visitRanges.getMinDanger(1), thirdWaveDanger, 0};
    }

    // search for the best movement plan
    Node root = new Node();
    Node best = root.search(Double.POSITIVE_INFINITY);
    Simulation s = best.s;
    clearState();

    root.updateVisitRange(visitRanges, true);
    Node ancestor = best;
    while (ancestor.parent != null) {
      currentPlan.add(0, ancestor);
      ancestor = ancestor.parent;
      ancestor.updateVisitRange(planVisitRanges, false);
    }

    // execute the next tick of the plan
    gs.setMyFutureStates(s.states.subList(1, s.states.size()).stream().map(
        state -> new BotState(state, gs.myState)).collect(Collectors.toList()));
    bot.setTurnRightRadians(Utils.normalRelativeAngle(
        s.states.get(1).heading - s.states.get(0).heading));
    bot.setAhead(s.path.get(0) == 0 ? ((strategy.antiRam ? 0 : Math.random() - 0.5) / 1e12) :
      s.path.get(0) * Double.POSITIVE_INFINITY);
    if (strategy.ram) {
      double targetHeading = s.getTargetHeading(gs.myState, gs.enemyState, s.path.get(0));
      double turn = FastTrig.normalRelativeAngle(targetHeading - bot.getHeadingRadians());
      bot.setMaxVelocity(Math.max(0, Math.abs(turn) < Math.PI / 9 ? 8 :
        8 - Math.abs(turn / (Math.PI / 6))));
    } else {
      bot.setMaxVelocity(8 - Math.random() / 1e12);
    }

    // paint movement information
    if (Painter.active) {
      //root.printTree();
      for (int i = 1; i < s.states.size(); i++) {
        Painter.THIS_TICK.addPoint(s.path.get(i - 1) == 0 ? Color.gray.brighter() :
          (s.path.get(i - 1) == 1 ? Color.yellow : Color.red), s.states.get(i).location);
      }
      Painter.THIS_TICK.addBot(Color.white, currentPlan.get(0).s.getEndLocation());
      if (currentPlan.size() > 1) {
        Painter.THIS_TICK.addBot(Color.gray, currentPlan.get(1).s.getEndLocation());
      }
      if (waves.size() > 0 && s.visitOffsetRanges.containsKey(waves.get(0))) {
        paintVisitOffsets(waves.get(0), s, Color.white);
        if (waves.size() > 1 && s.visitOffsetRanges.containsKey(waves.get(1))) {
          paintVisitOffsets(waves.get(1), s, Color.gray);
        }
      }
      if (best.approximateWaveLoc != null) {
        MovementWave w = waves.get(2);
        w.paintTick(Color.gray, w.getOffset(best.approximateWaveLoc.startGF));
        w.paintTick(Color.gray, w.getOffset(best.approximateWaveLoc.endGF));
      }
      for (MovementWave w : best.s.waves) {
        if (!waves.contains(w) && currentPlan.size() > 1) {
          w.radius = w.source.distance(currentPlan.get(1).s.getEndLocation());
          w.paintDangers(dangerEstimator.getDangers(w));
        }
      }
    }
  }

  public static double getWaveWeight(MovementWave w) {
    return (0.2 + w.power) / Math.sqrt(4 + Math.max(1, w.ticksUntilBreak));
  }

  private void paintVisitOffsets(MovementWave surfWave, Simulation s, Color color) {
    if (surfWave.radius > 0 && s.visitOffsetRanges.containsKey(surfWave)) {
      Range offsets = s.visitOffsetRanges.get(surfWave);
      surfWave.paintTick(color, offsets.start);
      surfWave.paintTick(color, offsets.end);
    }
  }

  // tracks reachable parts of incoming waves
  public class VisitRanges {
    public final Map<MovementWave, Range> ranges = new HashMap<>();

    private void add(MovementWave w, Range GFRange) {
      if (ranges.containsKey(w)) {
        ranges.get(w).merge(GFRange);
      } else {
        ranges.put(w, GFRange);
      }
    }

    private void addAll(VisitRanges visitRanges) {
      visitRanges.ranges.entrySet().stream().forEach(e -> add(e.getKey(), new Range(e.getValue())));
    }

    private double getMinDanger(int waveIndex) {
      if (waveIndex >= waves.size()) {
        return 0;
      }
      MovementWave w = waves.get(waveIndex);
      if (!ranges.containsKey(w)) {
        return 0;
      }
      Range range = ranges.get(w);
      return 0.9 * getWaveWeight(w) * dangerEstimator.getBestApproximateDanger(
          w, (gs.myState.location.distance(w.source) + 100), range).danger;
    }
  }

  public class Node implements Comparable<Node> {
    public final Node parent;
    public final MovementWave surfWave;
    public final int depth;
    public final Simulation s;
    public final TreeSet<Node> children = new TreeSet<>();
    public final DangerEstimator.ApproximateDangerLocation approximateWaveLoc;
    public final VisitRanges visitRanges = new VisitRanges();
    public final boolean fromCurrentPlan;

    public double waveDanger;
    public final double dangerMultiplier;
    public final double baseDanger;

    public Node() {
      parent = null;
      surfWave = null;
      s = new Simulation(strategy, waves);
      depth = 0;
      fromCurrentPlan = true;
      approximateWaveLoc = null;
      baseDanger = 0.01;
      dangerMultiplier = 1.0;
    }

    public Node(Node parent, List<Integer> extension, int antiRamEvadeSide, MovementWave surfWave) {
      this.parent = parent;
      this.surfWave = surfWave;
      s = new Simulation(parent.s, extension, antiRamEvadeSide);
      depth = parent.depth + 1;
      fromCurrentPlan = currentPlan.size() > parent.depth &&
          currentPlan.get(parent.depth).s.extension == extension;
      approximateWaveLoc = null;

      // simulate the execution of the path extension and estimate its danger
      s.simulate(surfWave, true, strategy.antiRam && depth == 1 && gs.enemyState.energy > 0);
      for (Map.Entry<MovementWave, Range> e : s.visitOffsetRanges.entrySet()) {
        MovementWave w = e.getKey();
        Range GFRange = w.getGFRange(e.getValue());
        waveDanger += getWaveWeight(w) * dangerEstimator.getDanger(w, GFRange);
        visitRanges.add(w, GFRange);
      }
      baseDanger = parent.baseDanger + strategy.getAntiMirrorDanger(
          s.states, waveDanger - parent.waveDanger);
      dangerMultiplier =
          depth == 1 || strategy.antiRam ? getDangerMultiplier() : parent.dangerMultiplier;
    }

    public Node(Node parent, MovementWave surfWave) {
      this.parent = parent;
      this.surfWave = surfWave;
      s = parent.s;
      depth = parent.depth + 1;
      fromCurrentPlan = parent.fromCurrentPlan;
      baseDanger = parent.baseDanger;
      waveDanger = parent.waveDanger;
      dangerMultiplier = parent.dangerMultiplier;

      // approximate simulation and danger estimation on the third wave (for efficiency)
      Simulation forward = new Simulation(s, Paths.FORWARD_PATH, s.antiRamEvadeSide);
      Simulation backward = new Simulation(s, Paths.BACKWARD_PATH, s.antiRamEvadeSide);
      forward.simulate(surfWave, false, false);
      backward.simulate(surfWave, false, false);
      double distance = Math.min(s.getEndLocation().distance(surfWave.source),
          Math.min(forward.getEndLocation().distance(surfWave.source),
                   backward.getEndLocation().distance(surfWave.source)));
      Range GFRange = Range.fromUnsorted(surfWave.getGF(forward.getEndLocation()),
                                         surfWave.getGF(backward.getEndLocation()));
      approximateWaveLoc = dangerEstimator.getBestApproximateDanger(surfWave, distance, GFRange);
      waveDanger += getWaveWeight(surfWave) * approximateWaveLoc.danger * THIRD_WAVE_DISCOUNT;
      visitRanges.add(surfWave, new Range(
          Math.max(GFRange.start - approximateWaveLoc.width/2, -0.99999),
          Math.min(GFRange.end + approximateWaveLoc.width/2, 0.99999)));
    }

    private double getDanger() {
      return (baseDanger + waveDanger) * dangerMultiplier;
    }

    private double getDangerMultiplier() {
      // scores the desirability of a position (outside of the wave danger)
      Point2D.Double endLocation = s.getEndLocation();
      Point2D.Double enemyLocation = gs.enemyState.location;
      double mul = Math.exp(s.states.get(0).location.distance(enemyLocation) /
          endLocation.distance(enemyLocation));  // stay away from enemies...
      if (strategy.ram) {
        return 1 / mul;  // ...unless we are ramming
      }
      if (!gs.enemyIsAlive) {
        mul = Math.pow(mul, 0.01);  // multiplier close to 1 if enemy is destroyed
      } else if (strategy.antiRam) {  // avoid simulated enemy and walls against a rambot
        mul /= Math.sqrt(s.getEndLocation().distance(s.predictedEnemyState.location));
        mul /= Math.sqrt(18 + BattleField.INSTANCE.wallDistance(endLocation));
        mul /= Geom.wallRestriction(enemyLocation, endLocation, 9);
      } else {  // try to increase our max escape angle and decrease the enemy's
        double bulletSpeed = Physics.bulletSpeed(gs.lastEnemyBulletPower);
        mul *= Geom.wallRestriction(endLocation, enemyLocation, bulletSpeed);
        mul /= MathUtils.sqr(Geom.wallRestriction(enemyLocation, endLocation, bulletSpeed));
      }
      return mul;
    }

    public Node search(double bestDanger) {
      addChildren();
      if (children.isEmpty()) {
        return this;
      }
      Node bestDescendent = null;
      for (Node child : children) {
        // A* pruning
        if (child.getDanger() + minFutureDanger[depth] * child.dangerMultiplier > bestDanger) {
          break;
        }
        Node descendent = child.search(bestDanger);
        if (descendent != null && descendent.getDanger() < bestDanger) {
          bestDescendent = descendent;
          bestDanger = descendent.getDanger();
        }
      }
      return bestDescendent;
    }

    private void addChildren() {
      if (depth == 3) {  // maximum search depth
        return;
      }
      MovementWave nextSurfWave = s.getSurfWave();
      if (nextSurfWave == null && depth > 0) {  // no waves left to surf
        return;
      }
      if (depth == 2) {  // approximate search on third wave
        if (!nextSurfWave.isVirtual) {
          children.add(new Node(this, nextSurfWave));
        }
      } else {
        if (strategy.antiRam) {   // precise search on the two nearest waves
          // against rambots consider both turn directions as well as forward/backward/halt
          addChild(Paths.HALT_PATH, 1, nextSurfWave);
          addChild(Paths.HALT_PATH, -1, nextSurfWave);
          addChild(Paths.FORWARD_PATH, 1, nextSurfWave);
          addChild(Paths.FORWARD_PATH, -1, nextSurfWave);
          addChild(Paths.BACKWARD_PATH, 1, nextSurfWave);
          addChild(Paths.BACKWARD_PATH, -1, nextSurfWave);
        } else {
          Node current = fromCurrentPlan && currentPlan.size() > depth ?
              addChild(currentPlan.get(depth).s.extension, nextSurfWave) : null;
          // always consider forward/backward/halting as move options
          Node forward = addChild(Paths.FORWARD_PATH, nextSurfWave);
          forward = forward == null ? current : forward;
          Node backward = addChild(Paths.BACKWARD_PATH, nextSurfWave);
          backward = backward == null ? current : backward;
          addChild(Paths.HALT_PATH, 1, nextSurfWave);
          if (nextSurfWave != null) {
            // consider two more paths that achieve low danger according to heuristics
            addChild(forward.getCandidateExtension(nextSurfWave), nextSurfWave);
            addChild(backward.getCandidateExtension(nextSurfWave), nextSurfWave);
          }
        }
      }
    }

    private Node addChild(List<Integer> extension, MovementWave surfWave) {
      return addChild(extension, 1, surfWave);
    }

    private Node addChild(List<Integer> extension, int antiRamEvadeSide, MovementWave surfWave) {
      // add a new child with the given path if it is not a duplicate
      if (extension != null && !extensionInChildren(extension, antiRamEvadeSide)) {
        Node child = new Node(this, extension, antiRamEvadeSide, surfWave);
        if (!extensionInChildren(child.s.extension, antiRamEvadeSide)) {
          children.add(child);
          return child;
        }
      }
      return null;
    }

    private boolean extensionInChildren(List<Integer> extension, int antiRamEvadeSide) {
      return children.size() > 0 &&
          children.stream().anyMatch(c -> extensionMatch(c, extension, antiRamEvadeSide));
    }

    private boolean extensionMatch(Node child, List<Integer> extension, int antiRamEvadeSide) {
      if (child.s.antiRamEvadeSide != antiRamEvadeSide) {
        return false;
      }
      int pathDirection = 0;
      int childDirection = 0;
      for (int i = 0; i < Math.max(extension.size(), child.s.extension.size()); i++) {
        if (i < extension.size()) {
          pathDirection = extension.get(i);
        }
        if (i < child.s.extension.size()) {
          childDirection = child.s.extension.get(i);
        }
        if (pathDirection != childDirection) {
          return false;
        }
      }
      return true;
    }

    private List<Integer> getCandidateExtension(MovementWave surfWave) {
      // simply randomize the desired velocity as exhaustive search is too expensive
      return getCandidateExtension(
          surfWave, Math.random() < 0.5 ? 0 : (Math.random() < 0.5 ? -8 : 8));
    }

    private List<Integer> getCandidateExtension(MovementWave surfWave, double targetVel) {
      if (s.states.size() <= 2) {
        return null;
      }
      // find the lowest-danger reachable point
      Point2D.Double bestPoint = null;
      double bestDanger = Double.MAX_VALUE;
      double bestDistance = 0;
      for (int i = s.startTick; i < s.states.size(); i++) {
        double danger = dangerEstimator.getApproximateDanger(surfWave, s.states.get(i).location);
        if (danger < bestDanger) {
          bestDanger = danger;
          bestDistance = i == s.startTick ? 0 : s.travelDistances.get(i);
          bestPoint = s.states.get(i).location;
        }
      }
      // look up a path that achieves the desired travel distance and target velocity
      return paths.getMatchingPath(s.states.get(0).velocity, Math.max(1,
          surfWave.getTicksUntilPasses(bestPoint, gs.gameTime) - 1 - s.startTick),
          bestDistance, targetVel);
    }

    public void updateVisitRange(VisitRanges ranges, boolean full) {
      for (Node child : children) {
        ranges.addAll(child.visitRanges);
        if (full) {
          child.updateVisitRange(ranges, full);
        }
      }
    }

    public void printTree() {
      if (depth > 0 && depth < 3) {
        for (int i = 0; i < (depth - 1) * 4; i++) {
          System.out.print(" ");
        }
        System.out.printf(s.extension + " %.2f", 1000 * waveDanger);
        if (depth == 1) {
          System.out.printf(" m=%.2f", dangerMultiplier);
        }
        if (depth == 2 && children.size() > 0) {
          System.out.printf(" -- %.2f", 1000 * children.pollFirst().waveDanger);
        }
        System.out.print(fromCurrentPlan ? "!" : "");
        System.out.println(s.extension == currentPlan.get(depth - 1).s.extension ? "*" : "");
      }
      children.stream().forEach(Node::printTree);
      if (depth == 0) {
        System.out.printf("Heuristic dangers: %.2f %.2f\n\n",
            1000 * minFutureDanger[0], 1000 * minFutureDanger[1]);
      }
    }

    @Override
    public int compareTo(Node other){
      return (int)Math.signum(getDanger() - (fromCurrentPlan ? 100 : 0) - other.getDanger() +
          (other.fromCurrentPlan ? 100 : 0));
    }
  }
}
