package com.contextsmith.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

public class RandomAccessUtil {

  public static void main(String[] args) throws IOException {
    if (args.length == 0 || args[0].isEmpty()) return;
    File file = new File(args[0]);
    RandomAccessFile raf = new RandomAccessFile(file, "r");
    MappedByteBuffer mbb = raf.getChannel().map(
        FileChannel.MapMode.READ_ONLY, 0, file.length());

    Random rand = new Random();
    long startTime = System.currentTimeMillis();
    int iops = 0;
    int trials = 10;
    byte[] bytes = new byte[8 * 1024];
    while (System.currentTimeMillis() - startTime < trials * 1000) {
      long randomPosition = nextLong(rand, file.length());
      mbb.position((int)randomPosition);
      mbb.get(bytes, 0, (int) Math.min(file.length() - randomPosition, bytes.length));
      ++iops;
    }
    raf.close();
    System.out.println((double) iops / trials);
  }

  private static long nextLong(Random rand, long n) {
    // error checking and 2^x checking removed for simplicity.
    long bits, val;
    do {
      bits = (rand.nextLong() << 1) >>> 1;
      val = bits % n;
    } while (bits - val + (n - 1) < 0L);
    return val;
  }

}
