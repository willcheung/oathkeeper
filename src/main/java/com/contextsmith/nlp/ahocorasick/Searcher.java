package com.contextsmith.nlp.ahocorasick;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
   Iterator returns a list of Search matches.
 */

public class Searcher implements Iterator<SearchResult> {

  // Returns true if byte 'b' is [0-9A-Za-z] (almost like \w in RE).
  private static boolean isAlphaNumeric(char c) {
    return ('0' <= c && c <= '9') ||
           ('A' <= c && c <= 'Z') ||
           ('a' <= c && c <= 'z');
  }

  /**
   * Continues the search, given the initial state described by the lastResult.
   * Package protected.
   */
  protected static SearchResult continueSearch(SearchResult lastResult) {
    char[] chars = lastResult.getChars();
    State state = lastResult.getLastMatchedState();

    for (int currIndex = lastResult.getLastIndex(); currIndex < chars.length;
         ++currIndex) {
      char c = chars[currIndex];
      State s;
      while ((s = state.get(c)) == null) {
        state = state.getFail();
      }
      state = s;

      // Continue lookup if no substrings ends at current index.
      if (state.getOutputs().length == 0) continue;

      // If current and next characters are both alphanumeric, then continue,
      // to prevent breaking a Latin word from the middle. This only check
      // the end boundary, not the begin boundary.
      if (currIndex + 1 < chars.length) {
        if (isAlphaNumeric(chars[currIndex]) &&
            isAlphaNumeric(chars[currIndex + 1])) {
          continue;
        }
      }
      return new SearchResult(state, chars, currIndex + 1);
    }
    return null;
  }

  private SearchResult currentResult;

  Searcher(SearchResult result) {
    this.currentResult = result;
  }

  @Override
  public boolean hasNext() {
    return (this.currentResult != null);
  }

  @Override
  public SearchResult next() {
    if (!hasNext()) throw new NoSuchElementException();
    SearchResult result = this.currentResult;
    this.currentResult = continueSearch(this.currentResult);
    return result;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
