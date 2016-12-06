package com.contextsmith.nlp.sentiment;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.contextsmith.utils.FileUtil;
import com.google.common.base.Splitter;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;

public class LexiconLoader {
  private static final Logger log = LoggerFactory.getLogger(LexiconLoader.class);

  public static final int NUM_FIELDS_PER_LINE = 4;

  public static String DEFAULT_UNIGRAM_LEXICON_PATH = "unigrams-pmilexicon.txt";
  public static String DEFAULT_BIGRAM_LEXICON_PATH = "bigrams-pmilexicon.txt";

  private Map<String, LexiconEntry> lexiconEntryMap;

  public LexiconLoader() {
    this.lexiconEntryMap = new HashMap<>();
  }

  public LexiconEntry get(String key) {
    return this.lexiconEntryMap.get(key);
  }

  public LexiconLoader load() throws IOException {
    load(DEFAULT_UNIGRAM_LEXICON_PATH);
    load(DEFAULT_BIGRAM_LEXICON_PATH);
    return this;
  }

  public void load(String lexiconPath) throws IOException {
    log.debug("Loading lexicon file: {}", lexiconPath);
    CharSource cs = FileUtil.findResourceAsCharSource(lexiconPath);

    Map<String, LexiconEntry> lexicon = cs.readLines(
        new LineProcessor<Map<String, LexiconEntry>>() {
          Map<String, LexiconEntry> lexicon = new HashMap<>();

          @Override
          public Map<String, LexiconEntry> getResult() {
            return this.lexicon;
          }

          @Override
          public boolean processLine(String line) {
            List<String> fields =
                Splitter.on('\t').trimResults().splitToList(line);
            if (fields.size() < NUM_FIELDS_PER_LINE) return true;

            String key = fields.get(0);
            if (StringUtils.isBlank(key)) return true;

            LexiconEntry entry = new LexiconEntry();
            entry.pmiScore = Double.parseDouble(fields.get(1));
            entry.numPositive = Integer.parseInt(fields.get(2));
            entry.numNegative = Integer.parseInt(fields.get(3));
            if (entry.numPositive > 0 && entry.numNegative > 0) {
                this.lexicon.put(key, entry);
            }
            return true;
          }
        }
    );
    this.lexiconEntryMap.putAll(lexicon);
  }
}