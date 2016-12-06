package com.contextsmith.nlp.sentiment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.nlp.sentiment.TwitterEntry.Sentiment;

public class SentimentEvaluator {

  private static final Logger log = LoggerFactory.getLogger(SentimentEvaluator.class);
  public static final String TEST_DATA = "src/test/resources/testdata.manual.2009.06.14.csv";
  public static final String TRAIN_DATA = "src/test/resources/training.1600000.processed.noemoticon.csv";

  public static void evaluate() throws IOException {
    List<TwitterEntry> entries = loadData(new File(TEST_DATA));
//    List<TwitterEntry> entries = loadData(new File(TRAIN_DATA));

    Accuracy accuracy = new Accuracy();
    for (TwitterEntry entry : entries) {
      if (entry.sentiment == Sentiment.NEUTRAL) continue;

      String tweet = normalizeTweet(entry.tweet);
      List<SentimentItem> sentences = SentimentScorer.process(tweet);
      double sentimentScore = 0;
      for (SentimentItem sentence : sentences) {
        sentimentScore += sentence.score;
      }
      if (sentimentScore == 0) {
        log.warn("Sentiment score is 0 for: {}", tweet);
        continue;
      }

      Sentiment prediction = null;
      if (sentimentScore > 0) prediction = Sentiment.POSITIVE;
      else if (sentimentScore < 0) prediction = Sentiment.NEGATIVE;

      if (entry.sentiment == prediction) {  // true
        if (prediction == Sentiment.POSITIVE) ++accuracy.truePos;
        else if (prediction == Sentiment.NEGATIVE) ++accuracy.trueNeg;
      } else {  // false
        if (prediction == Sentiment.POSITIVE) ++accuracy.falsePos;
        else if (prediction == Sentiment.NEGATIVE) ++accuracy.falseNeg;
//        log.debug("{}/{}: {}", entry.sentiment, sentimentScore, tweet);
      }
    }
    log.debug(accuracy.toString());
  }

  public static List<TwitterEntry> loadData(File file) throws IOException {
    CSVParser parser = CSVParser.parse(
        file, StandardCharsets.UTF_8, CSVFormat.EXCEL);
    List<TwitterEntry> entries = new ArrayList<>();
    for (CSVRecord record : parser) {
      TwitterEntry entry = new TwitterEntry();
      int sentimentId = Integer.parseInt(record.get(0));
      entry.setSentiment(sentimentId);
      entry.tweet = record.get(5);
      entries.add(entry);
    }
    return entries;
  }

  public static void main(String[] args) throws IOException {
    evaluate();
  }

  public static String normalizeTweet(String tweet) {
    tweet = tweet.replaceAll("[@#]\\w+", "");  // Removes hashes.
    tweet = tweet.replaceAll("http://\\S+", "");  // Removes URL.
    tweet = StringEscapeUtils.unescapeHtml4(tweet);
    tweet = tweet.replaceAll("\\s+", " ").trim();
    return tweet;
  }
}

class Accuracy {
  public int truePos = 0;
  public int trueNeg = 0;
  public int falsePos = 0;
  public int falseNeg = 0;

  public double computeNegAccuracy() {
    return (double) this.trueNeg / (this.trueNeg + this.falsePos);
  }

  public double computeOverallAccuracy() {
    return (double) (this.truePos + this.trueNeg) / (this.truePos + this.trueNeg + this.falsePos + this.falseNeg);
  }

  public double computePosAccuracy() {
    return (double) this.truePos / (this.truePos + this.falseNeg);
  }

  @Override
  public String toString() {
    return String.format("TP:%d TN:%d FP:%d FN:%d%nAcc Pos:%f, Neg:%f, Overall:%f",
                         this.truePos, this.trueNeg, this.falsePos, this.falseNeg,
                         computePosAccuracy(), computeNegAccuracy(),
                         computeOverallAccuracy());
  }
}

class TwitterEntry {
  enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE };
  public Sentiment sentiment;
  public String tweet;

  public void setSentiment(int id) {
    switch (id) {
    case 0: this.sentiment = Sentiment.NEGATIVE; break;
    case 2: this.sentiment = Sentiment.NEUTRAL; break;
    case 4: this.sentiment = Sentiment.POSITIVE; break;
    }
  }
}