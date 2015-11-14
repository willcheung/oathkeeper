package com.contextsmith.nlp.annotation;

import java.util.regex.Matcher;

import com.google.common.base.Joiner;
import com.google.common.collect.Range;

public class Annotation {

  private String[] values;
  private String text;      // Substring [begin..end) of a parent string.
  private String type;      // Annotation type.
  private int beginOffset;  // Begin offset of a parent string.
  private int endOffset;    // End offset of a parent string (exclusively).
  private int priority;     // Priority of this annotation.

  public Annotation(Matcher m, int groupId) {
    this(m.group(groupId), m.start(groupId), m.end(groupId));
  }

  public Annotation(String text, int beginOffset, int endOffset) {
    this.text = text;
    this.beginOffset = beginOffset;
    this.endOffset = endOffset;
  }

  public Integer getBeginOffset() {
    return this.beginOffset;
  }

  public Integer getEndOffset() {
    return this.endOffset;
  }

  public int getPriority() {
    return this.priority;
  }

  public Range<Integer> getRange() {
    return Range.closedOpen(this.beginOffset, this.endOffset);
  }

  public String getText() {
    return this.text;
  }

  public String getType() {
    return this.type;
  }

  public String[] getValues() {
    return this.values;
  }

  public int length() {
    return this.endOffset - this.beginOffset;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setValues(String[] values) {
    this.values = values;
  }

  public String toDebugString() {
    if (this.values == null) return this.toString();
    return String.format("%s => %s", this.toString(),
                         Joiner.on(", ").join(this.values));
  }

  @Override
  public String toString() {
    return String.format("%s {type: %s[%d], text: %s}", this.getRange(),
        this.type, this.priority, this.text);
  }
}