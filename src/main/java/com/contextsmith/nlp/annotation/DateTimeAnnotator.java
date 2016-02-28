package com.contextsmith.nlp.annotation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.joestelmach.natty.CalendarSource;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

public class DateTimeAnnotator extends AbstractAnnotator {

  private static final Logger log = LogManager.getLogger(DateTimeAnnotator.class);
  private static final TimeZone DEFAULT_TIME_ZONE = TimeZone.getDefault();
  private static DateTimeAnnotator instance = null;

  public static synchronized DateTimeAnnotator getInstance() {
    if (instance == null) {
      instance = new DateTimeAnnotator();
    }
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
    this(DEFAULT_TIME_ZONE);
  }

  public DateTimeAnnotator(TimeZone timezone) {
    super(DateTimeAnnotator.class.getSimpleName());
    log.info("Initializing date-time parser using default timezone: {}",
             timezone.getDisplayName());
    this.parser = new Parser(timezone);
  }

  public List<Annotation> annotate(Annotation parent, Date baseDate) {
    super.beforeAnnotateCore(parent.getText());
    List<Annotation> annotations = annotateCore(parent, baseDate);
    return super.afterAnnotateCore(annotations);
  }

  public List<Annotation> annotate(String text, Date baseDate) {
    return annotate(new Annotation(text), baseDate);
  }

  @Override
  protected List<Annotation> annotateCore(Annotation parent) {
    return annotateCore(parent, null);
  }

  private List<Annotation> annotateCore(Annotation parent, Date baseDate) {
    if (baseDate == null) baseDate = new Date();  // Current date.
    List<DateGroup> groups = null;

    synchronized(this.parser) {
      CalendarSource.setBaseDate(baseDate);  // Thread-specific.
      groups = this.parser.parse(parent.getText());
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
