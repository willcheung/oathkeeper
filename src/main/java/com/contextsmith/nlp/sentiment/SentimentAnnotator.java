package com.contextsmith.nlp.sentiment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.regex.Pattern;

import com.contextsmith.nlp.annotator.AbstractAnnotator;
import com.contextsmith.nlp.annotator.Annotation;
import com.contextsmith.utils.AnnotationUtil;

import edu.emory.mathcs.backport.java.util.Collections;

public class SentimentAnnotator extends AbstractAnnotator {
//  private static final Logger log = LoggerFactory.getLogger(SentimentAnnotator.class);

  public static final Pattern UNIGRAM_PATTERN =
      Pattern.compile("[@#\\w]+([^\\w\\s]+\\w+)*");
  public static final Pattern BIGRAM_PATTERN =
      Pattern.compile("(?<=^|\\s)[^\\w\\s@#]+|[^\\w\\s]+(?=\\s|$)|[@#\\w]+([^\\w\\s]+\\w+)*");

  private static SentimentAnnotator instance = null;

  public static synchronized SentimentAnnotator getInstance() throws IOException {
    if (instance == null) {
      instance = new SentimentAnnotator();
    }
    return instance;
  }

  public static LexiconEntry getPayload(Annotation ann) {
    Object o = ann.getPayload();
    if (o == null) return null;
    return (o instanceof LexiconEntry) ? (LexiconEntry) o : null;
  }

  public static void interactiveRun(SentimentAnnotator annotator) throws IOException {
    Scanner scanner = new Scanner(System.in);

    while (true) {
      System.out.print("Enter a sentence: ");
      String text = scanner.nextLine();
      if (text.isEmpty()) continue;
      if (text.toLowerCase().equals("exit")) break;
      List<Annotation> tokens = annotator.annotate(text);

      System.out.println("Found " + tokens.size() + " annotation(s)!");
      if (tokens.isEmpty()) continue;

      for (int i = 0; i < tokens.size(); ++i) {
        Annotation token = tokens.get(i);
        LexiconEntry entry = getPayload(token);
        System.out.println((i+1) + ". " + token.toDebugString() +
                           " | " + entry.toString());
      }
      double score = SentimentScorer.computeScore(tokens);
      System.out.println("Sentiment Score: " + score);

      /*Pair<Integer, Integer> offsets;
      offsets = SentimentScorer.findBestSubArray(tokens, false);
      System.out.println("Most Negative: " +
                         text.substring(offsets.getLeft(), offsets.getRight()));
      offsets = SentimentScorer.findBestSubArray(tokens, true);
      System.out.println("Most Positive: " +
                         text.substring(offsets.getLeft(), offsets.getRight()));*/
    }
    scanner.close();
  }

  public static void main(String[] args) throws IOException {
    interactiveRun(getInstance());
  }

  private LexiconLoader lexicon;

  public SentimentAnnotator() throws IOException {
    super(SentimentAnnotator.class.getSimpleName());
    super.setOutputLongestSpan(false);
    this.lexicon = new LexiconLoader().load();
  }

  @Override
  protected List<Annotation> annotateCore(Annotation parent) {
    List<Annotation> results = new ArrayList<>();
    results.addAll(annotateSentiment(parent, BIGRAM_PATTERN, 2, null));
    results.addAll(annotateSentiment(parent, UNIGRAM_PATTERN, 1, results));
    Collections.sort(results);
    return results;
  }

  private List<Annotation> annotateSentiment(Annotation parent,
                                             Pattern tokenizePat,
                                             int numGrams,
                                             List<Annotation> existingAnns) {
    List<Annotation> results = new ArrayList<>();
    List<Annotation> tokenAnns = AnnotationUtil.match(tokenizePat,
                                                      parent.getText());
    Queue<Annotation> annQueue = new LinkedList<>();  // Holds numGrams elements.

    for (int i = 0; i < tokenAnns.size(); ++i) {
      Annotation currAnn = tokenAnns.get(i);

      // Added 2016-06-14 to fix "How about 2:30pm on Thursday 6/16?"
      // where "6/16" is a very negative sentiment. No effect on evaluation.
      if (currAnn.getText().matches(".*\\d.*")) continue;

      // Added 2016-08-16 to prevent celebrity names from affecting sentiments.
      // This reduces accuracy on our evaluation data from 82% to 80%.
      if (currAnn.getText().matches("^[A-Z].*")) continue;

      annQueue.add(currAnn);
      if (annQueue.size() < numGrams) continue;
      if (annQueue.size() > numGrams) annQueue.remove();

      // At this point, queue.size() == numGrams
      LexiconEntry entry = findLexiconEntry(annQueue);
      if (entry == null) continue;

      Annotation multiTokenAnn = parent.subAnnotation(
          annQueue.peek().getBeginOffset(),  // Begin offset of first token.
          currAnn.getEndOffset());           // End offset of last token.

      // Current annotation cannot overlap with any existing annotations.
      if (existingAnns != null &&
          AnnotationUtil.isOverlapWithAny(multiTokenAnn, existingAnns)) {
        continue;
      }

      multiTokenAnn.setPayload(entry);
      multiTokenAnn.setType(super.getAnnotatorType());
      results.add(multiTokenAnn);
    }
    return results;
  }

  private LexiconEntry findLexiconEntry(Queue<Annotation> tokenQueue) {
    StringBuilder builder = new StringBuilder();
    for (Annotation token : tokenQueue) {
      if (token.getText().matches("^\\W+$")) return null;
      if (builder.length() > 0) builder.append(" ");
      builder.append(token.getText());
    }
    return this.lexicon.get(builder.toString().toLowerCase());
  }

}
