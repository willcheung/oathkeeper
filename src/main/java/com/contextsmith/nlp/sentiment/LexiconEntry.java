package com.contextsmith.nlp.sentiment;

public class LexiconEntry {
  double pmiScore = 0;
  int numPositive = 0;
  int numNegative = 0;

  @Override
  public String toString() {
    return String.format("%.3f (%d/%d)", this.pmiScore, this.numPositive,
                         this.numNegative);
  }
}