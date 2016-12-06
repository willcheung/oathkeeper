package com.contextsmith.nlp.annotator;

import java.util.regex.Matcher;

import com.google.common.base.Joiner;
import com.google.common.collect.Range;

public class Annotation implements Comparable<Annotation> {
  private String text;      // Substring [begin..end) of a parent string.
  private String type;      // Annotation type (or an unique id).

  private int beginOffset;  // Begin offset of a parent string.
  private int endOffset;    // End offset of a parent string (exclusively).
  private transient int priority;     // Priority of this annotation.

  private transient String[] values;  // For String values.
  private transient Object payload;   // For non-String values.

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

  public Annotation(Annotation parent, int beginOffset, int endOffset) {
    this.beginOffset = beginOffset;
    this.endOffset = endOffset;
    this.text = parent.getText().substring(
        beginOffset - parent.getBeginOffset(),
        endOffset - parent.getBeginOffset());
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

  @Override
  public int compareTo(Annotation other) {
    int r = Integer.compare(this.beginOffset, other.beginOffset);
    if (r != 0) return r;
    r = Integer.compare(this.endOffset, other.endOffset);
    if (r != 0) return r;
    return this.getText().compareTo(other.getText());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Annotation other = (Annotation) obj;
    if (this.beginOffset != other.beginOffset)
      return false;
    if (this.endOffset != other.endOffset)
      return false;
    if (this.text == null) {
      if (other.text != null)
        return false;
    } else if (!this.text.equals(other.text))
      return false;
    if (this.type == null) {
      if (other.type != null)
        return false;
    } else if (!this.type.equals(other.type))
      return false;
    return true;
  }

  public int getBeginOffset() {
    return this.beginOffset;
  }

  public int getEndOffset() {
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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + this.beginOffset;
    result = prime * result + this.endOffset;
    result = prime * result + ((this.text == null) ? 0 : this.text.hashCode());
    result = prime * result + ((this.type == null) ? 0 : this.type.hashCode());
    return result;
  }

  public boolean isOverlap(Annotation other) {
    return this.beginOffset < other.endOffset &&
           this.endOffset > other.beginOffset;
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