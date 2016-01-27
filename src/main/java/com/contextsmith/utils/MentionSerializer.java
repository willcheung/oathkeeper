package com.contextsmith.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.nlp.ahocorasick.AhoCorasick;
import com.contextsmith.nlp.annotation.Mention;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Stopwatch;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

public class MentionSerializer {

  static final Logger log = LogManager.getLogger(MentionSerializer.class);

  public static final String DEFAULT_KEYS_EXT = ".key";
  public static final String DEFAULT_VALUES_EXT = ".val";
  public static final double DEFAULT_JSON_SERIAL_RATIO = 0.8;
  public static final int DEFAULT_PRIORITY = 0;
  public static final int PROGRESS_UPDATE_GAP_IN_SEC = 5;
  public static final int PROGRESS_JSON_MAX_LENGTH = 140;
  public static final int BENCHMARK_TRIALS = 1000;

  public static void benchmarkValueFile(String valuesOutputPath, int numTrials)
      throws IOException {
    log.info("Benchmarking \"{}\" with {} trials.",
             valuesOutputPath, numTrials);

    ChronicleMap<Integer, MentionValue> indexMentionMap = ChronicleMapBuilder
        .of(Integer.class, MentionValue.class)
        .createPersistedTo(new File(valuesOutputPath));

    Random rand = new Random();
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < numTrials; ++i) {
      int randIndex = rand.nextInt(indexMentionMap.size());
      MentionValue mv = indexMentionMap.get(randIndex);
      if (mv == null) {
        log.error("Received 'null' value. File corrupted?");
        break;
      }
    }
    double elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
    log.info("Finished {} requests in {} seconds ({} iops)",
             numTrials, elapsedSec, Math.round(numTrials / elapsedSec));

    // A quick sanity check.
    String json1 = ProcessUtil.getJacksonInstance().writeValueAsString(
        indexMentionMap.get(0));
    String json2 = ProcessUtil.getJacksonInstance().writeValueAsString(
        indexMentionMap.get(indexMentionMap.size() - 1));
    log.info(json1);
    log.info(json2);

    indexMentionMap.close();
  }

  public static void main(String[] args)
      throws IOException, ClassNotFoundException {
    if (args.length == 0) {
      System.err.println("Please input the full path of the mention file.");
      return;
    }
    double jsonSerialRatio = DEFAULT_JSON_SERIAL_RATIO;
    if (args.length > 1) {
      jsonSerialRatio = Double.parseDouble(args[1]);
    }
    String mentionPath = args[0];
    if (StringUtils.isBlank(mentionPath)) return;

    String filename = mentionPath.replaceAll("\\.[^\\.]+$", "");
    String keysOutputPath = filename + DEFAULT_KEYS_EXT;
    String valuesOutputPath = filename + DEFAULT_VALUES_EXT;

    MentionSerializer.serialize(mentionPath, keysOutputPath, valuesOutputPath,
                                jsonSerialRatio, DEFAULT_PRIORITY);
    MentionSerializer.benchmarkValueFile(valuesOutputPath, BENCHMARK_TRIALS);
  }

  public static void serialize(String mentionInputPath, String keysOutputPath,
                               String valuesOutputPath, double jsonSerialRatio,
                               int priority)
      throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();

    File mentionInputFile = new File(mentionInputPath);
    log.info("Analyzing mention file: {}", mentionInputFile);
    int numLines = countLines(mentionInputFile);
    double numBytesPerLine = mentionInputFile.length() / numLines;
    log.info("Mention file contains {} lines (avg {} bytes per line).",
             numLines, numBytesPerLine);

    File valuesOutputFile = new File(valuesOutputPath);
    log.info("Serializing mention values to: {}", valuesOutputFile);
    if (valuesOutputFile.exists()) {
      log.warn("\"{}\" already exist, it will be deleted.", valuesOutputFile);
      if (!valuesOutputFile.delete()) {
        log.error("Failed to delete: {}", valuesOutputFile);
        return;
      }
    }

    ChronicleMap<Integer, MentionValue> indexMentionMap = ChronicleMapBuilder
        .of(Integer.class, MentionValue.class)
        .entries(numLines)
        .averageValueSize(jsonSerialRatio * numBytesPerLine)
        .valueMarshaller(new KryoSerializer<MentionValue>(MentionValue.class))
        .createPersistedTo(valuesOutputFile);

    TypeReference<MentionLine<SimpleMentionValue>> typeRef =
        new TypeReference<MentionLine<SimpleMentionValue>>(){};

    AhoCorasick ahoTree = new AhoCorasick();
    FileInputStream fis = new FileInputStream(mentionInputFile);
    BufferedReader br = new BufferedReader(new InputStreamReader(fis));

    long lastTimeMillis = System.currentTimeMillis();
    String line = null;
    for (int i = 1; (line = br.readLine()) != null; ++i) {
      if (StringUtils.isBlank(line)) continue;
      MentionLine<? extends SimpleMentionValue> mentionLine =
          ProcessUtil.getJacksonInstance().readValue(line.trim(), typeRef);

      int currIndex = indexMentionMap.size();
      ahoTree.add(mentionLine.getKey().toCharArray(), currIndex);

      MentionValue mv = new MentionValue();
      mv.setSmValues(mentionLine.getValues());
      mv.setCharLength(mentionLine.getKey().length());
      mv.setPriority(priority);
      indexMentionMap.put(currIndex, mv);

      if (System.currentTimeMillis() - lastTimeMillis >=
          PROGRESS_UPDATE_GAP_IN_SEC * 1000 || i == numLines) {
        // A quick sanity check.
        String json = ProcessUtil.getJacksonInstance().writeValueAsString(
            indexMentionMap.get(currIndex));
        if (json.length() > PROGRESS_JSON_MAX_LENGTH) {
          json = json.substring(0, PROGRESS_JSON_MAX_LENGTH) + "...";
        }
        log.info("[{}%] {} Processing: {}", Math.round(100.0 * i / numLines),
                 ProcessUtil.getHeapConsumption(), json);
        lastTimeMillis = System.currentTimeMillis();
      }
    }
    br.close();
    indexMentionMap.close();
    log.info("Serialized \"{}\" ({}) in {}.", valuesOutputFile,
             FileUtil.getReadableFileSize(valuesOutputPath), stopwatch);

    stopwatch.reset().start();
    log.info("Compiling AhoCorasick...");
    ahoTree.prepare();
    log.info("AhoCorasick compiled in {}.", stopwatch);

    SerializationUtil.serialize(ahoTree, keysOutputPath,
                                KryoSerializer.KRYOS.get());
  }

  private static int countLines(File mentionInputFile)
      throws IOException {
    FileInputStream fis = new FileInputStream(mentionInputFile);
    BufferedReader br = new BufferedReader(new InputStreamReader(fis));
    int numLines = 0;

    while (br.readLine() != null) {
      ++numLines;
    }
    br.close();
    return numLines;
  }
}

class MentionLine<V extends SimpleMentionValue> {
  private String key;
  private List<V> values;

  public String getKey() {
    return this.key;
  }

  public List<V> getValues() {
    return this.values;
  }
}

class MentionValue extends Mention implements Serializable {
  private static final long serialVersionUID = 1204762630832082773L;
  public List<? extends SimpleMentionValue> smValues;
  public List<? extends SimpleMentionValue> getSmValues() {
    return this.smValues;
  }
  public void setSmValues(List<? extends SimpleMentionValue> smValues) {
    this.smValues = smValues;
  }
}

class SimpleMentionValue implements Serializable {
  private static final long serialVersionUID = -6181462185459649554L;
  public String value;
  public String type;
  public String data;
  public double score;
}
