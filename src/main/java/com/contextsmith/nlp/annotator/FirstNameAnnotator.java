package com.contextsmith.nlp.annotator;

public class FirstNameAnnotator extends AbstractAnnotator {
  public static final String MENTION_PATH = "first-names.dict";
  public static final int MIN_CHARS = 3;
  public static final boolean IGNORE_CASE = true;

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
    super.setIgnoreCase(IGNORE_CASE);
  }
}
