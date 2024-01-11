package kc.mega.move;

import kc.mega.game.Physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import ags.utils.KdTree;

/**
 * Looks up movement paths based on desired distance and end velocity.
 * A path is a list of 1s, 0s, and -1s (forward/halt/backward for that tick)
 */
public class Paths {
  public static final List<Integer> FORWARD_PATH = Collections.singletonList(1);
  public static final List<Integer> BACKWARD_PATH = Collections.singletonList(-1);
  public static final List<Integer> HALT_PATH = Collections.singletonList(0);

  private static final int MAX_PATH_LENGTH = 50;
  private static final double VELOCITY_STEP = 0.5;
  private static final double VELOCITY_DISTANCE_SCALE = 3;
  private final List<List<KdTree<List<Integer>>>> pathTrees;

  public Paths() {
    pathTrees = new ArrayList<>();
    for (double velocity = 0; velocity < 8.001; velocity += VELOCITY_STEP) {
      List<KdTree<List<Integer>>> velocityTrees = new ArrayList<>();
      for (int i = 0; i < MAX_PATH_LENGTH; i++) {
        velocityTrees.add(new KdTree.Manhattan<List<Integer>>(3, 10000));
      }
      addPaths(1, velocity, 0, new ArrayList<>(), velocityTrees, false);
      addPaths(0, velocity, 0, new ArrayList<>(), velocityTrees, false);
      addPaths(-1, velocity, 0, new ArrayList<>(), velocityTrees, false);
      pathTrees.add(velocityTrees);
    }
  }

  private void addPaths(int direction, double velocity, double distance, List<Integer> path,
      List<KdTree<List<Integer>>> trees, boolean dirChanged) {
    if (path.size() == MAX_PATH_LENGTH) {
      return;
    }
    if (!dirChanged) {
      // branch (only allowed once per path)
      addPaths(direction == 1 ? -1 : direction + 1, velocity, distance,
          new ArrayList<>(path), trees, true);
      addPaths(direction == -1 ? 1 : direction - 1, velocity, distance,
          new ArrayList<>(path), trees, true);
    }
    // extend the current path
    path.add(Math.abs(velocity) > 1.99 && velocity * direction < 0 ? 0 : direction);
    velocity = Physics.nextVelocity(velocity, direction);
    distance += velocity;
    trees.get(path.size() - 1).addPoint(new double[] {
        velocity * VELOCITY_DISTANCE_SCALE, distance, Math.random()}, path);
    addPaths(direction, velocity, distance, new ArrayList<>(path), trees, dirChanged);
  }

  public List<Integer> getMatchingPath(double velocity, int ticks, double targetDistance,
      double targetEndVelocity) {
    int reversal = velocity < 0 ? -1 : 1;
    KdTree<List<Integer>> pathTree = pathTrees.get(
        (int)(reversal * velocity / VELOCITY_STEP)).get(Math.min(ticks, MAX_PATH_LENGTH) - 1);
    List<Integer> path = pathTree.nearestNeighbor(new double[] {
        VELOCITY_DISTANCE_SCALE * reversal * targetEndVelocity,
        reversal * targetDistance, Math.random()}, 1, false).get(0).value;
    if (reversal == 1) {
      return path;
    } else {
      return path.stream().map(d -> -d).collect(Collectors.toList());
    }
  }
}
