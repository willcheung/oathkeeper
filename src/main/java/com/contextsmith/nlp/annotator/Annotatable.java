package com.contextsmith.nlp.annotator;

import java.util.List;

import com.contextsmith.nlp.ahocorasick.AhoCorasick;

public interface Annotatable {
  public List<Annotation> annotate(Annotation annotation);
  public List<Annotation> annotate(String text);
  public AhoCorasick getAhoCorasick();
  public String getAnnotatorType();
  public List<Mention> getMentionList();
  public int getMinChars();
  public boolean isIgnoreCase();
  public boolean isTitleCase();
  public void setMinChars(int minChineseChars);
}
