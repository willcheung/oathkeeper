package com.contextsmith.nlp.annotation;

public class ValedictionAnnotator extends AbstractAnnotator {

  public static final String MENTION_PATH = "valedictions.dict";
  public static final int MIN_CHARS = 1;
  public static final boolean TRY_IGNORE_CASE = true;
  public static final boolean IS_PREFIX_MATCH = true;

  private static ValedictionAnnotator instance = null;

  public static synchronized ValedictionAnnotator getInstance() {
    if (instance == null) {
      instance = new ValedictionAnnotator();
      instance.loadData(MENTION_PATH);
      instance.compile();
    }
    return instance;
  }

  public static void main(String[] args) {
    interactiveRun(getInstance());
  }

  public ValedictionAnnotator() {
    super(MENTION_PATH);
    super.setMinChars(MIN_CHARS);
    super.setIgnoreCase(TRY_IGNORE_CASE);
    super.setPrefixMatch(IS_PREFIX_MATCH);
  }
}
