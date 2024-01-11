package kc.mega.model;

import kc.mega.game.GameState;
import kc.mega.wave.WaveWithFeatures;

/** Base class for machine learning models. */
public abstract class Model {
  public final String name;

  public Model(String name) {
    this.name = name;
  }

  public void train(WaveWithFeatures w) {};

  public void onTurn(GameState gs) {};
}