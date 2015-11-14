package com.contextsmith.nlp.ahocorasick;

/**
   <p>Holds the result of the search so far.  Includes the outputs where
   the search finished as well as the last index of the matching.</p>

   <p>(Internally, it also holds enough state to continue a running
   search, though this is not exposed for public use.)</p>
 */
public class SearchResult {
  private final State lastMatchedState;
  private final char[] chars;
  private final int lastIndex;

  SearchResult(State lastMatchedState, char[] chars, int lastIndex) {
    this.lastMatchedState = lastMatchedState;
    this.chars = chars;
    this.lastIndex = lastIndex;
  }

  public char[] getChars() {
    return this.chars;
  }

  /**
       Returns the index where the search terminates.  Note that this
       is one byte after the last matching character.
   */
  public int getLastIndex() {
    return this.lastIndex;
  }

  public State getLastMatchedState() {
    return this.lastMatchedState;
  }

  /**
       Returns a list of the outputs of this match.
   */
  public int[] getOutputs() {
    return this.lastMatchedState.getOutputs();
  }
}
