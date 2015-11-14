package com.contextsmith.utils;

import java.lang.ref.WeakReference;

import com.google.common.base.Strings;

public class ProcessUtil {

  public static final String CONFIG_PROP_FILE = "config.properties";
//  private static final Properties CONFIG_PROP =
//      FileUtil.loadProperties(CONFIG_PROP_FILE);

  public static void die() {
    die((String) null);
  }

  public static void die(Exception e) {
    if (e != null) e.printStackTrace();
    System.exit(-1);
  }

  public static void die(String msg) {
    if (!Strings.isNullOrEmpty(msg)) {
      System.err.println(msg);
    }
    System.exit(-1);
  }

  /**
   * This method guarantees that garbage collection is
   * done unlike <code>{@link System#gc()}</code>
   */
  public static void forceGc() {
    Object obj = new Object();
    WeakReference<Object> ref = new WeakReference<>(obj);
    obj = null;
    while (ref.get() != null) System.gc();
  }

  /*public static Properties getConfigProperties() {
    return CONFIG_PROP;
  }*/

  public static String getHeapConsumption() {
    long heapSize = Runtime.getRuntime().totalMemory() / 1024 / 1024;
    long freeSize = Runtime.getRuntime().freeMemory() / 1024 / 1024;
    long heapMaxSize = Runtime.getRuntime().maxMemory() / 1024 / 1024;
    return String.format("%d/%d/%d MB Used",
                         heapSize - freeSize, heapSize, heapMaxSize);
  }

  public static double getPercentMemoryUsed() {
    return (double) Runtime.getRuntime().totalMemory() /
        Runtime.getRuntime().maxMemory();
  }
}
