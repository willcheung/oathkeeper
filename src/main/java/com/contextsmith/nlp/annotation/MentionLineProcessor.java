package com.contextsmith.nlp.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import com.contextsmith.nlp.ahocorasick.AhoCorasick;
import com.contextsmith.utils.ProcessUtil;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.common.io.LineProcessor;

public class MentionLineProcessor implements LineProcessor<Object> {

  static final Logger log = LogManager.getLogger(
      MentionLineProcessor.class.getName());

  private static boolean isJson(String text) {
    return text.startsWith("{") && text.endsWith("}");
  }

  private AhoCorasick ahoCorasick;
  private List<Mention> mentionList;
  private int minChars;
  private boolean isIgnoreCase;
  private boolean isTitleCase;
  private Set<String> stopwords;

  public MentionLineProcessor(Annotatable annotator) {
    this.ahoCorasick = annotator.getAhoCorasick();
    this.mentionList = annotator.getMentionList();
    this.minChars = annotator.getMinChars();
    this.isTitleCase = annotator.isTitleCase();
    this.isIgnoreCase = annotator.isIgnoreCase();
    this.stopwords = annotator.getStopwords();
  }

  @Override
  public Object getResult() {
    return null;
  }

  @Override
  public boolean processLine(String line) throws IOException {
    line = line.trim();
    if (line.isEmpty()) return true;

    String mention = null;
    List<String> values = new ArrayList<>();

    if (isJson(line)) {
      // TODO(rcwang): Implement a container class for this JSON line.
    } else {
      Iterable<String> entries = Splitter.on('\t').trimResults().split(line);
      Iterator<String> iter = entries.iterator();
      if (iter.hasNext()) mention = iter.next().trim();
      else return true;  // No mention? Invalid entry.

      while (iter.hasNext()) {
        String value = iter.next();
        if (!Strings.isBlank(value)) values.add(value);
      }
    }

    if (mention == null || mention.length() < this.minChars) return true;
    if (this.isIgnoreCase) {
      mention = mention.toLowerCase();
    } else if (this.isTitleCase) {
      mention = WordUtils.capitalizeFully(mention);
    }
    processMentionValues(mention, values);
    return true;
  }

  private boolean processMentionValues(String mentionStr, List<String> values) {
    mentionStr = mentionStr.trim();
    if (mentionStr.isEmpty()) return false;

    // Create a mutable set.
    Set<String> variants = Sets.newHashSet(mentionStr);

    // Remove stopwords from the variant set.
    removeStopwords(variants);
    if (variants.isEmpty()) return false;

    Mention mention = new Mention();
    mention.setCharLength(mentionStr.length());
    mention.setValues(values.toArray(new String[values.size()]));
    this.mentionList.add(mention);

    for (String variant : variants) {
      // The 'key' for AhoCorasick is the 'variant', and
      // the value is the index of this variant in the 'mentionList'.
      this.ahoCorasick.add(variant.toCharArray(), this.mentionList.size() - 1);

      if (this.mentionList.size() % 1e4 == 0) {
        log.info("{}. {}, Storing: {}", this.mentionList.size(),
            ProcessUtil.getHeapConsumption(), variant);
      }
    }
    return true;
  }

  private void removeStopwords(Iterable<String> words) {
    for (Iterator<String> iter = words.iterator(); iter.hasNext();) {
      String word = iter.next();

      if (this.stopwords.contains(word.toLowerCase().trim())) {
        log.trace("\"{}\" is a stopword, not loading it.", word);
        iter.remove();
      }
    }
  }
}
