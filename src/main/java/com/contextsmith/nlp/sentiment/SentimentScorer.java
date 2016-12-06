package com.contextsmith.nlp.sentiment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.analysis.function.Sigmoid;

import com.contextsmith.nlp.annotator.Annotation;
import com.contextsmith.nlp.annotator.SentenceAnnotator;

public class SentimentScorer {
  // Assumes minimum PMI score is -MAX_PMI_SCORE.
  public static final double MAX_PMI_SCORE = 5;

  /*public static void findBestSubArray(int[] inputs, boolean isMaxSum) {
    int bestStartIndex = 0;
    int bestEndIndex = 0;
    int bestSum = isMaxSum ? Integer.MIN_VALUE : Integer.MAX_VALUE;

    int cumulativeSum = 0;
    int bestStartIndexUntilNow = 0;

    for (int currentIndex = 0; currentIndex < inputs.length; currentIndex++) {
      int eachArrayItem = inputs[currentIndex];
      cumulativeSum += eachArrayItem;

      if (isMaxSum && cumulativeSum > bestSum ||
          !isMaxSum && cumulativeSum < bestSum) {
        bestSum = cumulativeSum;
        bestStartIndex = bestStartIndexUntilNow;
        bestEndIndex = currentIndex;
      }
      if (isMaxSum && cumulativeSum < 0 ||
          !isMaxSum && cumulativeSum > 0) {
        bestStartIndexUntilNow = currentIndex + 1;
        cumulativeSum = 0;
      }
    }

    System.out.println("Best sum         : " + bestSum);
    System.out.println("Best start index : " + bestStartIndex);
    System.out.println("Best end index   : " + bestEndIndex);
  }*/

  public static double computeScore(List<Annotation> tokens) {
    if (tokens.isEmpty()) return 0;
    double sumPmiScore = 0;
    for (Annotation token : tokens) {
      LexiconEntry entry = SentimentAnnotator.getPayload(token);
      sumPmiScore += entry.pmiScore;
    }
    // Returns the sigmoid'-ed PMI score.
    return new Sigmoid(-1, 1).value(sumPmiScore);
  }

  public static Pair<Integer, Integer> findBestSubArray(
      List<Annotation> annotations,
      boolean isMaxSum) {
    if (annotations.isEmpty()) return null;

    int bestBeginIndex = 0;
    int bestEndIndex = 0;
    int bestBeginIndexUntilNow = 0;

    double bestSum = isMaxSum ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    double cumulativeSum = 0;

    for (int i = 0; i < annotations.size(); ++i) {
      Annotation annotation = annotations.get(i);
      LexiconEntry entry = SentimentAnnotator.getPayload(annotation);
      cumulativeSum += entry.pmiScore;

      if (isMaxSum && cumulativeSum > bestSum ||
          !isMaxSum && cumulativeSum < bestSum) {
        bestSum = cumulativeSum;
        bestBeginIndex = bestBeginIndexUntilNow;
        bestEndIndex = i;
      }
      if (isMaxSum && cumulativeSum < 0 ||
          !isMaxSum && cumulativeSum > 0) {
        bestBeginIndexUntilNow = i + 1;
        cumulativeSum = 0;
      }
    }
    return Pair.of(annotations.get(bestBeginIndex).getBeginOffset(),
                   annotations.get(bestEndIndex).getEndOffset());
  }

  public static void main(String[] args) {
//    int[] intArr={3, -1, -1, -1, -1, -1, 2, 0, 0, 0 };
//    int[] intArr = {-1, 3, -5, 4, 6, -1, 2, -7, 13, -3};
//    int[] intArr={-6,-2,-3,-4,-1,-5,-5};
//    int[] intArr={-631, -4, -33};
//    int[] intArr={-1, 2, 3, -3, 4};
//    findBestSubArray(intArr, false);
  }

  public static List<SentimentItem> process(Annotation parent)
      throws IOException {
    List<SentimentItem> items = new ArrayList<>();
    List<Annotation> sentences = SentenceAnnotator.getInstance().annotate(parent);

    for (Annotation sentence : sentences) {
      List<Annotation> tokens =
          SentimentAnnotator.getInstance().annotate(sentence);
      if (tokens.isEmpty()) continue;

      SentimentItem item = new SentimentItem();
      item.sentence = sentence;
      item.score = computeScore(tokens);

      /*if (item.score >= 0) {
        Pair<Integer, Integer> offsets = findBestSubArray(tokens, true);
        item.mostPosPhrase = parent.subAnnotation(offsets.getLeft(),
                                                      offsets.getRight());
      } else {
        Pair<Integer, Integer> offsets = findBestSubArray(tokens, false);
        item.mostNegPhrase = parent.subAnnotation(offsets.getLeft(),
                                                      offsets.getRight());
      }*/
      items.add(item);
    }
    return items;
  }

  public static List<SentimentItem> process(String text)
      throws IOException {
    return process(new Annotation(text));
  }

}