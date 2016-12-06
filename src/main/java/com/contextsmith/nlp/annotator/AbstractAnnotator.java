package com.contextsmith.nlp.annotator;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.nlp.ahocorasick.AhoCorasick;
import com.contextsmith.nlp.ahocorasick.SearchResult;
import com.contextsmith.utils.AnnotationUtil;
import com.contextsmith.utils.FileUtil;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Chars;

public abstract class AbstractAnnotator implements Annotatable {
  private static final Logger log = LoggerFactory.getLogger(AbstractAnnotator.class);

  public static final int DEFAULT_MIN_CHARS = 1;
  public static final boolean DEFAULT_PREFIX_MATCH = false;
  public static final boolean DEFAULT_SUFFIX_MATCH = false;
  public static final boolean DEFAULT_TITLE_CASE = false;
  public static final boolean DEFAULT_TRY_IGNORE_CASE = false;
  public static final boolean DEFAULT_OUTPUT_LONGEST_SPAN = true;

  // Define the valid characters one before (prefix) a valid token.
  public static final Set<Character> VALID_EN_BEGIN_CHARS =
      new HashSet<>(Chars.asList(" /|[{(>/?!\"“';:,~".toCharArray()));

  // Define the valid characters one after (suffix) a valid token.
  public static final Set<Character> VALID_EN_END_CHARS =
      new HashSet<>(Chars.asList(" ~,.:;'’”\"!?/<)}]|".toCharArray()));

  public static void interactiveRun(Annotatable annotator) {
    Scanner scanner = new Scanner(System.in);
    while (true) {
      System.out.print("Enter a sentence: ");
      String text = scanner.nextLine();
      if (text.isEmpty()) continue;
      if (text.toLowerCase().equals("exit")) break;
      List<Annotation> annotations = annotator.annotate(text);

      System.out.println("Found " + annotations.size() + " annotation(s)!");
      for (int i = 0; i < annotations.size(); ++i) {
        Annotation annotation = annotations.get(i);
        System.out.println((i+1) + ". " + annotation.toDebugString());
      }
    }
    scanner.close();
  }

  private static boolean hasValidBoundingChars(String text, int startPos,
                                               int endPos) {
    // Test the character that is one char before the token.
    boolean validStart = false;
    validStart = (startPos == 0) ? true :
      VALID_EN_BEGIN_CHARS.contains(text.charAt(startPos - 1));
    if (!validStart) return false;
    // Test the character that is one char after the token.
    boolean validEnd = false;
    validEnd = (endPos == text.length()) ? true :
      VALID_EN_END_CHARS.contains(text.charAt(endPos));
    return validEnd;
  }

  private AhoCorasick ahoCorasick;  // A super efficient matching algorithm.
  private List<Mention> mentionList;  // Required for AhoCorasick.
  private String annotatorType;

  private boolean isIgnoreCase;
  private boolean isTitleCase;
  private boolean isPrefixMatch;
  private boolean isSuffixMatch;
  private boolean isOutputLongestSpan;
  private int minChars;
  private int priority;

  public AbstractAnnotator(String annotatorType) {
    checkArgument(annotatorType != null, "Annotator ID cannot be null");

    this.annotatorType = annotatorType.replaceAll("\\.[^\\.]*$", "");

    this.minChars = DEFAULT_MIN_CHARS;
    this.isTitleCase = DEFAULT_TITLE_CASE;
    this.isPrefixMatch = DEFAULT_PREFIX_MATCH;
    this.isSuffixMatch = DEFAULT_SUFFIX_MATCH;
    this.isIgnoreCase = DEFAULT_TRY_IGNORE_CASE;
    this.isOutputLongestSpan = DEFAULT_OUTPUT_LONGEST_SPAN;

    this.ahoCorasick = new AhoCorasick();
    this.mentionList = new ArrayList<>();
  }
  @Override
  public List<Annotation> annotate(Annotation annotation) {
    beforeAnnotateCore(annotation.getText());
    List<Annotation> annotations = annotateCore(annotation);
    return afterAnnotateCore(annotations);
  }

  @Override
  public List<Annotation> annotate(String text) {
    return annotate(new Annotation(text));
  }

  public void compile() {
    log.info("Compiling aho-corasick tree... ");
    Stopwatch stopwatch = Stopwatch.createStarted();
    this.ahoCorasick.prepare();
    log.info("Finished compilation in {}", stopwatch);
  }

  public boolean contains(String s) {
    return this.get(s) != null;
  }

  public Mention get(String s) {
    if (s == null) return null;
    Iterator<SearchResult> iter = this.ahoCorasick.search(s.toCharArray());

    while (iter.hasNext()) {
      SearchResult result = iter.next();
      for (int i : result.getOutputs()) {
        Mention mention = this.mentionList.get(i);
        if (mention.getCharLength() == s.length()) {
          return mention;
        }
      }
    }
    return null;
  }

  @Override
  public AhoCorasick getAhoCorasick() {
    return this.ahoCorasick;
  }

  @Override
  public String getAnnotatorType() {
    return this.annotatorType;
  }

  @Override
  public List<Mention> getMentionList() {
    return this.mentionList;
  }

  @Override
  public int getMinChars() {
    return this.minChars;
  }

  public int getPriority() {
    return this.priority;
  }

  public boolean hasPrefix(String prefix) {
    if (prefix == null) return false;
    return this.ahoCorasick.hasPrefix(prefix.toCharArray());
  }

  @Override
  public boolean isIgnoreCase() {
    return this.isIgnoreCase;
  }

  public boolean isOutputLongestSpan() {
    return this.isOutputLongestSpan;
  }

  public boolean isPrefixMatch() {
    return this.isPrefixMatch;
  }

  public boolean isSuffixMatch() {
    return this.isSuffixMatch;
  }

  @Override
  public boolean isTitleCase() {
    return this.isTitleCase;
  }

  public void loadData(String dataPath) {
    // Setup a processor to process every line inside the dictionary files.
    MentionLineProcessor lineProcessor =
        new MentionLineProcessor(this);
    log.info("Loading mentions from: {}", dataPath);
    try {
      FileUtil.findResourceAsCharSource(dataPath).readLines(lineProcessor);
    } catch (IOException e) {
      log.error("Error loading data: {}", e.getMessage());
    }
  }

  public void put(String key, int value) {
    this.ahoCorasick.add(key.toCharArray(), value);
  }

  public void setAhoCorasick(AhoCorasick ahoCorasick) {
    this.ahoCorasick = ahoCorasick;
  }

  public void setIgnoreCase(boolean tryIgnoreCase) {
    this.isIgnoreCase = tryIgnoreCase;
  }

  @Override
  public void setMinChars(int minChars) {
    this.minChars = minChars;
  }

  public void setOutputLongestSpan(boolean isOutputLongestSpan) {
    this.isOutputLongestSpan = isOutputLongestSpan;
  }

  public void setPrefixMatch(boolean isPrefixMatch) {
    this.isPrefixMatch = isPrefixMatch;
  }

  public void setPriority(int priority) {
    checkArgument(priority >= Mention.MIN_PRIORITY &&
                  priority <= Mention.MAX_PRIORITY,
                  String.format("Priority should be within %d and %d.",
                                Mention.MIN_PRIORITY, Mention.MAX_PRIORITY));
    this.priority = priority;
  }

  public void setSuffixMatch(boolean isSuffixMatch) {
    this.isSuffixMatch = isSuffixMatch;
  }

  public void setTitleCase(boolean isTitleCase) {
    this.isTitleCase = isTitleCase;
  }

  protected List<Annotation> afterAnnotateCore(List<Annotation> annotations) {
    if (annotations.isEmpty()) return annotations;
    if (log.isTraceEnabled()) log.trace(AnnotationUtil.toDebugString(annotations));

    if (this.isOutputLongestSpan) {
      annotations = AnnotationUtil.getLongestSpans(annotations);
      if (log.isTraceEnabled()) log.trace(AnnotationUtil.toDebugString(annotations));
    }
    return annotations;
  }

//  protected List<Annotation> annotateCore(String text, int parentBeginOffset) {
  protected List<Annotation> annotateCore(Annotation parent) {
    String text = parent.getText();
    Iterator<SearchResult> iter = this.ahoCorasick.search(
        this.isIgnoreCase ? text.toLowerCase().toCharArray() : text.toCharArray());
    List<Annotation> annotations = new ArrayList<>();

    while (iter.hasNext()) {
      SearchResult result = iter.next();
      int endOffset = result.getLastIndex();
      if (this.isSuffixMatch && endOffset != text.length()) continue;

      for (int index : result.getOutputs()) {
        Mention mention = this.mentionList.get(index);
        int beginOffset = result.getLastIndex() - mention.getCharLength();
        if (this.isPrefixMatch && beginOffset != 0) continue;

        // Check for character boundaries.
        if (!hasValidBoundingChars(text, beginOffset, endOffset)) {
          continue;
        }

        // Convert mention to annotation, and store it somewhere.
        Annotation annotation = parent.subAnnotation(beginOffset, endOffset);
        annotation.setValues(mention.getValues());
        annotation.setPriority(mention.getPriority());
        annotation.setType(this.getAnnotatorType());
        annotations.add(annotation);
      }
    }
    return annotations;
  }

  protected void beforeAnnotateCore(String text) {
    log.trace(String.format("Annotating %s: %s", this.getAnnotatorType(), text));
  }
}
