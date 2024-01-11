package kc.mega.utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import robocode.AdvancedRobot;
import robocode.RobocodeFileOutputStream;

/** Writes out data for for offline learning. */
public enum DatasetWriter {
  INSTANCE;
  public static final float END_ROUND_CODE = 123456;

  private boolean active = false;
  private AdvancedRobot bot;
  private String enemyName = "";
  private String timestamp = "";
  private Map<String, RobocodeFileOutputStream> outStreams = new HashMap<>();

  public void onBattleStart(AdvancedRobot bot) {
    this.bot = bot;
  }

  public void setEnemyName(String name) {
    if (enemyName.isEmpty()) {
      enemyName = name.replace(" ", "-");
      timestamp = new SimpleDateFormat("MM-dd-HH:mm:ss").format(new Date());
    }
  }

  public void write(String filename, double[] data) {
    if (active) {
      try {
        getOutstream(filename).write(encodeDoubleArray(data));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void write(String filename, String data) {
    if (active) {
      try {
         new PrintStream(getOutstream(filename)).print(data + "\n");
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private RobocodeFileOutputStream getOutstream(String filename) throws IOException {
    String fname = enemyName + "_" + timestamp + "_" + filename;
    if (!outStreams.containsKey(fname)) {
      File f = bot.getDataFile(fname);
      String filepath = f.getAbsolutePath();
      System.out.println("New file: " + filepath);
      outStreams.put(fname, new RobocodeFileOutputStream(f));
    }
    return outStreams.get(fname);
  }

  private static byte[] encodeDoubleArray(double[] x) {
    byte[] bytes = new byte[4 * x.length];
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
    for (int i = 0; i < x.length; i++) {
      for (int j = 0; j < 4; j++) {
        buffer.putFloat(4 * i, (float)x[i]);
      }
    }
    return bytes;
  }

  public void onRoundEnd() {
    if (active) {
      for (String fname : outStreams.keySet()) {
        if (!fname.endsWith(".txt")) {
          write(fname.split("_")[2], new double[] {DatasetWriter.END_ROUND_CODE});
        }
      }
    }
  }
}
