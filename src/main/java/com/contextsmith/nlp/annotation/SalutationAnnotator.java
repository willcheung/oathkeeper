package com.contextsmith.nlp.annotation;

public class SalutationAnnotator extends AbstractAnnotator {

  public static final String MENTION_PATH = "salutations.dict";
  public static final int MIN_CHARS = 1;
  public static final boolean IGNORE_CASE = true;
  public static final boolean IS_PREFIX_MATCH = true;

  private static SalutationAnnotator instance = null;

  public static synchronized SalutationAnnotator getInstance() {
    if (instance == null) {
      instance = new SalutationAnnotator();
      instance.loadData(MENTION_PATH);
      instance.compile();
    }
    return instance;
  }

  public static void main(String[] args) {
    interactiveRun(getInstance());
  }

  public SalutationAnnotator() {
    super(MENTION_PATH);
    super.setMinChars(MIN_CHARS);
    super.setIgnoreCase(IGNORE_CASE);
    super.setPrefixMatch(IS_PREFIX_MATCH);
  }
}
