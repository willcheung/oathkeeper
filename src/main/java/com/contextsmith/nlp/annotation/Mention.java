package com.contextsmith.nlp.annotation;

import static com.google.common.base.Preconditions.checkArgument;

public class Mention {

  public static final int MIN_PRIORITY = 0;
  public static final int MAX_PRIORITY = (1 << Byte.SIZE) - 1;

  // The value string.
  private String[] values;
  // Character array length of the subject (string length).
  private int charLength;
  // Mention internal priority (-128 to 127).
  private byte priority;

  public Mention() {
    this.charLength = 0;
    this.values = null;
    this.priority = Byte.MIN_VALUE;
  }

  public int getCharLength() {
    return this.charLength;
  }

  public int getPriority() {
    return (this.priority - Byte.MIN_VALUE);
  }

  public String[] getValues() {
    return this.values;
  }

  public void setCharLength(int length) {
    this.charLength = length;
  }

  public void setPriority(int priority) {
    checkArgument(priority >= MIN_PRIORITY && priority <= MAX_PRIORITY,
                  String.format("Priority should be within %d and %d.",
                                MIN_PRIORITY, MAX_PRIORITY));
    this.priority = (byte)(priority + Byte.MIN_VALUE);
  }

  public void setValues(String[] values) {
    this.values = values;
  }
}