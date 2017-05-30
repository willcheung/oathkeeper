package com.contextsmith.nlp.time;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.nlp.annotator.AbstractAnnotator;
import com.contextsmith.nlp.annotator.Annotation;
import com.contextsmith.nlp.annotator.SentenceAnnotator;
import com.contextsmith.utils.MimeMessageUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.joestelmach.natty.DateGroup;

public class TaskAnnotator extends AbstractAnnotator {
  private static final Logger log = LoggerFactory.getLogger(TaskAnnotator.class);

  public static final int DEFAULT_WINDOW_SIZE_IN_CHARS = 200;  // Tweet length
  public static final Set<Character> ENDING_CLAUSE_PUNCTS =
      Sets.newHashSet(',', ';', '!', '?');
  public static final SimpleDateFormat DATE_FORMAT =
      new SimpleDateFormat("EEE, MM/dd/yyyy h:mm a z");
  public static final Pattern NON_CRUCIAL_DATE_PATTERN = Pattern.compile(
      "(?i)\\b(now|today|may|thus|\\d+-\\d+)\\b");
  public static final Pattern ENDS_WITH_POSITIVE_WORDS = Pattern.compile(
      "(?i)\\b(good|nice|excellent|wonderful)\\s+$");
  public static final Pattern IS_MEETING_REQUEST = Pattern.compile(
      "(?i)\\b(?:(un)?availab(le|ility)|connect|meet(ing)?|schedule|(re)?schedule)\\b");
  public static final Pattern IS_PROMISE_REQUEST = Pattern.compile(
      "(?i)\\b(?:will|\\w+'ll|should)\\b");
  public static final Pattern IS_PRESENT_PART_REQUEST = Pattern.compile(
      "(?i)\\b(?:am|is|are|i'm|\\w+'re|\\w's)\\s+(?:\\w+ing)\\b");

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

    String mailDateStr = "27 Feb 2016 16:38:35 -0500";
    ZonedDateTime baseDate = ZonedDateTime.from(
        MimeMessageUtil.MAIL_DATE_FORMATTER.parse(mailDateStr));

    Stopwatch stopwatch = Stopwatch.createStarted();
    String s =
        "I am available this weekend. Have a nice weekend!";
//        "this is just a test mail.\r\n\r\nPlease reply before next Monday.";
        /*"Please deliver the report by tomorrow 1am. " +
        "We will discuss this in more detail next Monday. " +
        "We will discuss about the details about the project on Feb 28. " +
        "We will meet at McDonalds on Milthilda Ave on Thursday, March 3rd. " +
        "Let’s meet inside the lobby of Building 41 next thursday at 5pm. " +
        "The assignment is due Jan. 28, 2016, right before our final exam. " +
        "We will be moving the team outing to next thursday, March 3rd.";*/

    Annotation ann = new Annotation(s);
    List<Annotation> tasks = TaskAnnotator.getInstance().annotate(ann, baseDate);
    log.debug("# tasks: {}", tasks.size());
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
    this(DEFAULT_WINDOW_SIZE_IN_CHARS);
  }

  public TaskAnnotator(int windowSizeInChars) {
    super(TaskAnnotator.class.getSimpleName());
    this.windowSizeInChars = windowSizeInChars;
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

  @Override
  protected List<Annotation> annotateCore(Annotation parent) {
    return annotateCore(parent, null);
  }

  private List<Annotation> annotateCore(Annotation parent,
                                        ZonedDateTime baseDate) {
    List<Annotation> tasks = new ArrayList<>();
    log.trace("Segmentting sentences for: {}", parent);
    List<Annotation> sentences =
        SentenceAnnotator.getInstance().annotate(parent);
    log.trace("Found {} sentences!", sentences.size());

    for (Annotation sentence : sentences) {
      // We want only promise request sentences
      // (eg. I will, we should, We are going to, etc)
      if (!IS_PROMISE_REQUEST.matcher(sentence.getText()).find() &&
          !IS_PRESENT_PART_REQUEST.matcher(sentence.getText()).find()) {
        continue;
      }
      // We do not want meeting request sentences.
      if (IS_MEETING_REQUEST.matcher(sentence.getText()).find()) {
        continue;
      }
      // We only want valid English sentences.
      // NOTE(rcwang): Because of the promise request, this is unnecessary.
      /*double enScore =
          EnglishScorer.getInstance().computeScore(sentence.getText());
      if (enScore == 0) {
        log.trace("English score {} is less than {} for: {}",
                  enScore, EnglishEmailTextParser.MIN_SENTENCE_SCORE, sentence);
        continue;
      }*/

      log.trace("Annotating date time...");
      List<Annotation> dates =
          DateTimeAnnotator.getInstance().annotate(sentence, baseDate);
      for (Annotation date : dates) {
        // Ignore expressions like "today", "may", etc.
        if (NON_CRUCIAL_DATE_PATTERN.matcher(date.getText()).find()) {
          continue;
        }
        // Ignore sentences having "nice [weekend]"
        String preDateTimeSubstring = sentence.getText().substring(
            0, date.getBeginOffset() - sentence.getBeginOffset());
        if (ENDS_WITH_POSITIVE_WORDS.matcher(preDateTimeSubstring).find()) {
          continue;
        }
        log.trace("Finding task window...");
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
