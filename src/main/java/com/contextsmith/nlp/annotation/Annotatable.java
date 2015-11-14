package com.contextsmith.nlp.annotation;

import java.util.List;
import java.util.Set;

import com.contextsmith.nlp.ahocorasick.AhoCorasick;

public interface Annotatable {
  public List<Annotation> annotate(String text);
  public AhoCorasick getAhoCorasick();
  public String getAnnotatorType();
  public List<Mention> getMentionList();
  public int getMinChars();
  public Set<String> getStopwords();
  public boolean isIgnoreCase();
  public boolean isTitleCase();
  public void setMinChars(int minChineseChars);
}
