package kc.mega.move.models;

import kc.mega.model.Model;
import kc.mega.wave.WaveWithFeatures;


/** Base class for a wave surfing model that assigns dangers to guessfactor bins. */
public abstract class DangerModel extends Model {
  public DangerModel(String name) {
    super(name);
  }

  public abstract double[] getDangers(WaveWithFeatures w);
}