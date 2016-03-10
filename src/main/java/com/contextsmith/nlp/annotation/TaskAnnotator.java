package com.contextsmith.nlp.annotation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.nlp.email.parser.EnglishEmailBodyParser;
import com.contextsmith.nlp.email.parser.EnglishScorer;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.joestelmach.natty.DateGroup;

public class TaskAnnotator extends AbstractAnnotator {

  private static final Logger log = LogManager.getLogger(TaskAnnotator.class);

  public static final int DEFAULT_WINDOW_SIZE_IN_CHARS = 140;  // Tweet length
  public static final Set<Character> ENDING_CLAUSE_PUNCTS =
      Sets.newHashSet(',', ';', '!', '?');
  public static final SimpleDateFormat DATE_FORMAT =
      new SimpleDateFormat("EEE, MM/dd/yyyy h:mm a z");
  public static final Set<String> NON_CRUCIAL_DATES =
      Sets.newHashSet("now", "today", "may");

  private static TaskAnnotator instance = null;

  public static synchronized TaskAnnotator getInstance() {
    if (instance == null) instance = new TaskAnnotator();
    return instance;
  }

  public static Annotation getPayload(Annotation ann) {
    Object o = ann.getPayload();
    if (o == null) return null;
    return (o instanceof Annotation) ? (Annotation) o : null;
  }

  public static void main(String[] args) throws ParseException {
//    interactiveRun(getInstance());

    String mailDateStr = "Sat, 27 Feb 2016 16:38:35 -0500";
    ZonedDateTime baseDate = ZonedDateTime.from(
        MimeMessageUtil.MAIL_DATE_FORMATTER.parse(mailDateStr));

    Stopwatch stopwatch = Stopwatch.createStarted();
    String s =
        "Please deliver the report by tomorrow 1am. " +
        "We will discuss this in more detail next Monday. " +
        "We will discuss about the details about the project on Feb 28. " +
        "We will meet at McDonalds on Milthilda Ave on Thursday, March 3rd. " +
        "Let’s meet inside the lobby of Building 41 next thursday at 5pm. " +
        "The assignment is due Jan. 28, 2016, right before our final exam. " +
        "We will be moving the team outing to next thursday, March 3rd.";

    Annotation ann = new Annotation(s);
    List<Annotation> tasks = TaskAnnotator.getInstance().annotate(ann, baseDate);
    for (Annotation task : tasks) {
      log.debug("{} -> {}", toReadableDate(task), task);
    }
    log.debug("Elapsed time: {}", stopwatch);
  }

  private static String toReadableDate(Annotation task) {
    Annotation date = TaskAnnotator.getPayload(task);
    DateGroup group = DateTimeAnnotator.getPayload(date);
    if (group == null) return null;

    StringBuilder builder = new StringBuilder();
    for (Date d : group.getDates()) {
      if (builder.length() > 0) builder.append(", ");
      ZonedDateTime zdt = ZonedDateTime.ofInstant(d.toInstant(),
                                                  ZoneId.systemDefault());
      builder.append(MimeMessageUtil.MAIL_DATE_FORMATTER.format(zdt));
    }
    return String.format("%s (%s, %s)", builder, group.isDateInferred(),
                         group.isTimeInferred());
  }

  private int windowSizeInChars;

  public TaskAnnotator() {
    super(TaskAnnotator.class.getSimpleName());
    this.windowSizeInChars = DEFAULT_WINDOW_SIZE_IN_CHARS;
  }

  public List<Annotation> annotate(Annotation parent, ZonedDateTime baseDate) {
    super.beforeAnnotateCore(parent.getText());
    List<Annotation> annotations = annotateCore(parent, baseDate);
    return super.afterAnnotateCore(annotations);
  }

  public List<Annotation> annotate(String text, ZonedDateTime baseDate) {
    return annotate(new Annotation(text), baseDate);
  }

  public int getWindowSizeInChars() {
    return this.windowSizeInChars;
  }

  public void setWindowSizeInChars(int windowSizeInChars) {
    this.windowSizeInChars = windowSizeInChars;
  }

  @Override
  protected List<Annotation> annotateCore(Annotation parent) {
    return annotateCore(parent, null);
  }

  private List<Annotation> annotateCore(Annotation parent,
                                        ZonedDateTime baseDate) {
    List<Annotation> tasks = new ArrayList<>();
    List<Annotation> sentences =
        SentenceAnnotator.getInstance().annotate(parent);

    for (Annotation sentence : sentences) {
      // Check if this is a valid English sentence.
      double enScore =
          EnglishScorer.getInstance().computeScore(sentence.getText());
      if (enScore < EnglishEmailBodyParser.MIN_SENTENCE_SCORE) continue;

      List<Annotation> dates =
          DateTimeAnnotator.getInstance().annotate(sentence, baseDate);
      for (Annotation date : dates) {
        if (NON_CRUCIAL_DATES.contains(date.getText())) continue;
        Annotation task = findTaskWindow(sentence, date,
                                         this.windowSizeInChars);
        if (task != null) tasks.add(task);
      }
    }
    return tasks;
  }

  private Annotation findTaskWindow(Annotation sentence, Annotation date,
                                    int windowSizeInChars) {
    if (sentence.length() <= windowSizeInChars) {
      Annotation ann = sentence.subAnnotation(0, sentence.length());
      return initAnnotation(ann, date);
    }
    if (date.length() >= windowSizeInChars) {
      Annotation ann = sentence.subAnnotation(
          date.getBeginOffset(),
          date.getBeginOffset() + windowSizeInChars);
      return initAnnotation(ann, date);
    }

    int numPunctInWindow = 0;
    boolean isLastLeftPunct = false;
    boolean isLastRightPunct = false;
    int minNumPunct = Integer.MAX_VALUE;
    List<Integer> bestLeftIndices = new ArrayList<>();

    int leftIndex = Math.max(sentence.getBeginOffset(),
                             date.getEndOffset() - windowSizeInChars);
    int rightIndex = leftIndex + windowSizeInChars;

    while (leftIndex <= date.getBeginOffset() &&
        rightIndex <= sentence.getEndOffset()) {
      if (isLastLeftPunct) --numPunctInWindow;
      if (isLastRightPunct) ++numPunctInWindow;

      if (numPunctInWindow < minNumPunct) {
        minNumPunct = numPunctInWindow;
        bestLeftIndices.clear();
      }
      if (numPunctInWindow == minNumPunct) {
        bestLeftIndices.add(leftIndex);
      }

      isLastLeftPunct = ENDING_CLAUSE_PUNCTS.contains(
          sentence.getText().charAt(leftIndex - sentence.getBeginOffset()));

      isLastRightPunct = false;
      if (rightIndex < sentence.getEndOffset()) {
        isLastRightPunct = ENDING_CLAUSE_PUNCTS.contains(
            sentence.getText().charAt(rightIndex - sentence.getBeginOffset()));
      }
      ++leftIndex;
      ++rightIndex;
    }

    int bestLeftPos = -1;
    double minOffsetFromCenter = Integer.MAX_VALUE;
    for (int leftPos : bestLeftIndices) {
      // Smaller the better, minimum value is 0.
      double offsetFromCenter = Math.abs(
          0.5 * (date.getBeginOffset() + date.getEndOffset()) -
          0.5 * (2 * leftPos + windowSizeInChars));
      if (offsetFromCenter < minOffsetFromCenter) {
        minOffsetFromCenter = offsetFromCenter;
        bestLeftPos = leftPos;
      }
    }

    int bestRightPos = bestLeftPos + windowSizeInChars;
    Annotation ann = sentence.subAnnotation(
        bestLeftPos - sentence.getBeginOffset(),
        bestRightPos - sentence.getBeginOffset());
    return initAnnotation(ann, date);
  }

  private Annotation initAnnotation(Annotation ann, Annotation date) {
    ann.setPayload(date);
    ann.setPriority(this.getPriority());
    ann.setType(this.getAnnotatorType());
    return ann;
  }

}
