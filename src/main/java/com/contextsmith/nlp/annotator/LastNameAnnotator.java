package com.contextsmith.nlp.annotator;

public class LastNameAnnotator extends AbstractAnnotator {
  public static final String MENTION_PATH = "last-names.dict";
  public static final int MIN_CHARS = 3;
  public static final boolean IS_TITLE_CASE = true;

  private static LastNameAnnotator instance = null;

  public static synchronized LastNameAnnotator getInstance() {
    if (instance == null) {
      instance = new LastNameAnnotator();
      instance.loadData(MENTION_PATH);
      instance.compile();
    }
    return instance;
  }

  public static void main(String[] args) {
    interactiveRun(getInstance());
  }

  public LastNameAnnotator() {
    super(MENTION_PATH);
    super.setMinChars(MIN_CHARS);
    super.setTitleCase(IS_TITLE_CASE);
  }
}
