package com.contextsmith.nlp.time;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import com.contextsmith.nlp.annotator.AbstractAnnotator;
import com.contextsmith.nlp.annotator.Annotation;
import com.joestelmach.natty.CalendarSource;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

public class DateTimeAnnotator extends AbstractAnnotator {
//  private static final Logger log = LoggerFactory.getLogger(DateTimeAnnotator.class);
  private static DateTimeAnnotator instance = null;

  public static synchronized DateTimeAnnotator getInstance() {
    if (instance == null) instance = new DateTimeAnnotator();
    return instance;
  }

  public static DateGroup getPayload(Annotation ann) {
    Object o = ann.getPayload();
    if (o == null) return null;
    return (o instanceof DateGroup) ? (DateGroup) o : null;
  }

  public static void main(String[] args) {
    interactiveRun(getInstance());
  }

  private static boolean hasValidBoundingChar(int beginOffset, int endOffset,
                                              String text) {
    if (beginOffset > 0 && beginOffset < text.length()) {
      char c = text.charAt(beginOffset - 1);
      if (Character.isLetterOrDigit(c)) return false;
    }
    if (endOffset > 0 && endOffset < text.length()) {
      char c = text.charAt(endOffset);
      if (Character.isLetterOrDigit(c)) return false;
    }
    return true;
  }

  private Parser parser;

  public DateTimeAnnotator() {
    super(DateTimeAnnotator.class.getSimpleName());
    this.parser = new Parser();
  }

  public List<Annotation> annotate(Annotation parent, ZonedDateTime baseDate) {
    super.beforeAnnotateCore(parent.getText());
    List<Annotation> annotations = annotateCore(parent, baseDate);
    return super.afterAnnotateCore(annotations);
  }

  public List<Annotation> annotate(String text, ZonedDateTime baseDate) {
    return annotate(new Annotation(text), baseDate);
  }

  public void setParserTimeZone(TimeZone timezone) {
    Field field = null;
    try {
      field = Parser.class.getDeclaredField("_defaultTimeZone");
    } catch (NoSuchFieldException | SecurityException e) {
      e.printStackTrace();
    }
    field.setAccessible(true);
    try {
      field.set(this.parser, timezone);
    } catch (IllegalArgumentException | IllegalAccessException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected List<Annotation> annotateCore(Annotation parent) {
    return annotateCore(parent, null);
  }

  private List<Annotation> annotateCore(Annotation parent,
                                        ZonedDateTime baseDate) {
    List<DateGroup> groups = null;

    // If baseDate is null, use current date.
    Date instant = (baseDate == null) ?
        new Date() : Date.from(baseDate.toInstant());

    // If timeZone is null, use JVM's default time-zone.
    TimeZone timeZone = (baseDate == null) ?
        TimeZone.getDefault() : TimeZone.getTimeZone(baseDate.getZone());

    setParserTimeZone(timeZone);
    try {
      groups = this.parser.parse(parent.getText(), instant);

    } catch (Exception e) {  // Ignore all exceptions.
    }

    List<Annotation> annotations = new ArrayList<>();
    if (groups == null) return annotations;

    for (DateGroup group : groups) {
      int startPos = group.getAbsolutePosition();
      int endPos = startPos + group.getText().length();
      if (!hasValidBoundingChar(startPos, endPos, group.getFullText())) continue;

      Annotation ann = parent.subAnnotation(startPos, endPos);
      ann.setType(super.getAnnotatorType());
      ann.setPayload(group);
      annotations.add(ann);
    }
    return annotations;
  }
}
