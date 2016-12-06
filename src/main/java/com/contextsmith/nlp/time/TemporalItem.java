package com.contextsmith.nlp.time;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.nlp.annotator.Annotation;
import com.joestelmach.natty.DateGroup;

public class TemporalItem {
  private static final Logger log = LoggerFactory.getLogger(TemporalItem.class);

  public static TemporalItem newInstance(Annotation taskAnn) {
    Annotation dateAnn = TaskAnnotator.getPayload(taskAnn);
    DateGroup group = DateTimeAnnotator.getPayload(dateAnn);
    List<Date> dates = group.getDates();
    if (dateAnn == null || group == null || dates.isEmpty()) {
      log.error("Problem initializing temporal item!");
      return null;
    }
    if (group.isDateInferred()) {
      log.trace("Ignoring inferred date: {}", dateAnn.getText());
      return null;
    }

    TemporalItem item = new TemporalItem();
    item.taskAnnotation = taskAnn;
    item.dateTimeAnnotation = dateAnn;
    item.hasTime = !group.isTimeInferred();
    item.resolvedDates.add(dates.get(0).getTime() / 1000);
    if (dates.size() > 1) {
      item.resolvedDates.add(dates.get(1).getTime() / 1000);
    }
    return item;
  }

  public boolean hasTime = false;
  public Annotation taskAnnotation = null;
  public Annotation dateTimeAnnotation = null;
  public List<Long> resolvedDates = new ArrayList<>();
}