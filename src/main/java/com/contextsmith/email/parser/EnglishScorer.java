package com.contextsmith.email.parser;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.utils.FileUtil;

public class EnglishScorer {
  private static final Logger log = LoggerFactory.getLogger(EnglishScorer.class);

  // Word probabilities obtained from: http://norvig.com/mayzner.html
  public static final String DEFAULT_EN_WORD_PROB_FILE = "top-en-words-prob.txt";
  public static final Pattern EN_WORD_TOKEN_RE = Pattern.compile("[a-zA-Z]+");

  private static EnglishScorer instance = null;

  public static EnglishScorer getInstance() {
    if (instance == null) instance = new EnglishScorer().loadData();
    return instance;
  }

  public static void main(String[] args) {
    EnglishScorer scorer = new EnglishScorer();
    scorer.loadData();
    double score = scorer.computeScore("hello world!");
    log.debug("Score: " + score);
  }

  public static List<String> tokenize(String latinText) {
    List<String> words = new ArrayList<>();
    Matcher m = EN_WORD_TOKEN_RE.matcher(latinText);
    while (m.find()) {
      words.add(m.group());
    }
    return words;
  }

  private static Map<String, Double> computeFreqDist(String text) {
    Map<String, Double> resultMap = new HashMap<>();
    Matcher m = EN_WORD_TOKEN_RE.matcher(text.toLowerCase());
    int total = 0;

    while (m.find()) {
      String word = m.group();
      Double count = resultMap.get(word);
      if (count == null) count = 0.0;
      resultMap.put(m.group(), ++count);
      ++total;
    }
    for (String word : resultMap.keySet()) {
      double count = resultMap.get(word);
      resultMap.put(word, count / total);
    }
    return resultMap;
  }

  private Map<String, Double> wordProbMap;
  private double wordProbMagnitude;

  EnglishScorer() {
    this.wordProbMap = new HashMap<>();
    this.wordProbMagnitude = 0;
  }

  public double computeScore(String text) {
    Map<String, Double> freqDist = computeFreqDist(text);
    return cosineSimilarity(freqDist);
  }

  public EnglishScorer loadData() {
    return loadData(DEFAULT_EN_WORD_PROB_FILE);
  }

  public EnglishScorer loadData(String path) {
    List<String> lines = FileUtil.findResourceAsStringList(path);
    for (String line : lines) {
      if (StringUtils.isBlank(line)) continue;
      String[] parts = line.split("\\s+");
      if (parts.length != 2 || StringUtils.isBlank(parts[0]) ||
          StringUtils.isBlank(parts[1])) {
        log.error("Error in file: " + path);
        continue;
      }
      double prob = Double.parseDouble(parts[1].trim());
      this.wordProbMap.put(parts[0].trim(), prob);
      this.wordProbMagnitude += Math.pow(prob, 2);
    }
    this.wordProbMagnitude = Math.sqrt(this.wordProbMagnitude);
    return this;
  }

  private double cosineSimilarity(Map<String, Double> inputMap) {
    checkNotNull(inputMap);
    double dotProduct = 0;
    double inputMagnitude = 0;

    for (Entry<String, Double> entry : inputMap.entrySet()) {
      String word = entry.getKey();
      double inputProb = entry.getValue();
      inputMagnitude += Math.pow(inputProb, 2);

      Double wordProb = this.wordProbMap.get(word);
      if (wordProb != null) dotProduct += inputProb * wordProb;
    }
    inputMagnitude = Math.sqrt(inputMagnitude);
    if (inputMagnitude == 0 || this.wordProbMagnitude == 0) return 0;
    return dotProduct / (inputMagnitude * this.wordProbMagnitude);
  }
}
