package com.contextsmith.nlp.annotation;

public class FirstNameAnnotator extends AbstractAnnotator {

  public static final String MENTION_PATH = "first-names.dict";
  public static final String EN_STOPWORD_PATH = "en-stopwords.txt";

  public static final int MIN_CHARS = 3;
  public static final boolean TRY_IGNORE_CASE = true;

  private static FirstNameAnnotator instance = null;

  public static synchronized FirstNameAnnotator getInstance() {
    if (instance == null) {
      instance = new FirstNameAnnotator();
      instance.loadData(MENTION_PATH);
      instance.compile();
    }
    return instance;
  }

  public static void main(String[] args) {
    interactiveRun(getInstance());
  }

  public FirstNameAnnotator() {
    super(MENTION_PATH);
    super.setMinChars(MIN_CHARS);
    super.setIgnoreCase(TRY_IGNORE_CASE);
    super.loadStopwords(EN_STOPWORD_PATH);
  }
}
