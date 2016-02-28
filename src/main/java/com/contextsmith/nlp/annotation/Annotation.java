package com.contextsmith.nlp.annotation;

import java.util.regex.Matcher;

import com.google.common.base.Joiner;
import com.google.common.collect.Range;

public class Annotation {

  private String[] values;  // For String values.
  private Object payload;   // For non-String values.

  private String text;      // Substring [begin..end) of a parent string.
  private String type;      // Annotation type (or an unique id).

  private int beginOffset;  // Begin offset of a parent string.
  private int endOffset;    // End offset of a parent string (exclusively).
  private int priority;     // Priority of this annotation.

  // Clone
  public Annotation(Annotation ann) {
    this.text = ann.getText();
    this.beginOffset = ann.getBeginOffset();
    this.endOffset = ann.getEndOffset();
    this.type = ann.getType();
    this.priority = ann.getPriority();
    this.payload = ann.getPayload();
    this.values = ann.getValues();
  }

  public Annotation(Matcher m, int groupId) {
    this(m.group(groupId), m.start(groupId));
  }

  public Annotation(String text) {
    this(text, 0);
  }

  public Annotation(String text, int beginOffset) {
    this.text = text;
    this.beginOffset = beginOffset;
    this.endOffset = beginOffset + text.length();
  }

  public Integer getBeginOffset() {
    return this.beginOffset;
  }

  public Integer getEndOffset() {
    return this.endOffset;
  }

  public Object getPayload() {
    return this.payload;
  }

  @SuppressWarnings("unchecked")
  public <T> T getPayload(T obj) {
    return (T) this.payload;
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

  public void setPayload(Object payload) {
    this.payload = payload;
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

  public Annotation subAnnotation(int beginOffset, int endOffset) {
    return new Annotation(this.text.substring(beginOffset, endOffset),
                          this.beginOffset + beginOffset);
  }

  public String toDebugString() {
    if (this.values == null) return this.toString();
    return String.format("%s => %s", this.toString(),
                         Joiner.on(", ").join(this.values));
  }

  @Override
  public String toString() {
    return String.format("[P%d] %s %s: \"%s\"", this.priority, this.getRange(),
                         this.type, this.text);
  }
}