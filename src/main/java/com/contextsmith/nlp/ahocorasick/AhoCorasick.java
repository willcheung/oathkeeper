package com.contextsmith.nlp.ahocorasick;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
   <p>An implementation of the Aho-Corasick string searching
   automaton.  This implementation of the <a
   href="http://portal.acm.org/citation.cfm?id=360855&dl=ACM&coll=GUIDE"
   target="_blank">Aho-Corasick</a> algorithm is optimized to work
   with bytes.</p>

   <p>
   Example usage:
   <code><pre>
       AhoCorasick tree = new AhoCorasick();
       tree.add("hello".getBytes(), "hello");
       tree.add("world".getBytes(), "world");
       tree.prepare();

       Iterator searcher = tree.search("hello world".getBytes());
       while (searcher.hasNext()) {
           SearchResult result = searcher.next();
           System.out.println(result.getOutputs());
           System.out.println("Found at index: " + result.getLastIndex());
       }
   </pre></code>
   </p>
 */
public class AhoCorasick {
  private final RootState root;
  private boolean isPrepared;

  public AhoCorasick() {
    this.root = new RootState();
    this.isPrepared = false;
  }

  /**
   * Adds a new keyword with the given output.  During search, if
   * the keyword is matched, output will be one of the yielded
   * elements in SearchResults.getOutputs().
   */
  public void add(char[] keyword, int output) {
    if (this.isPrepared) throw new IllegalStateException(
        "Can't add keywords after prepare() is called.");
    State lastState = this.root.extendAll(keyword);
    lastState.addOutput(output);
  }

  /**
   * Returns true if input bytes is a prefix of one of the strings in the tree.
   */
  public boolean hasPrefix(char[] chars) {
    State state = this.root;
    for (char c : chars) {
      state = state.get(c);
      if (state == null) return false;
    }
    return true;
  }

  /**
   * Prepares the automaton for searching.  This must be called
   * before any searching().
   *
   * DANGER DANGER: dense algorithm code ahead.  Very order
   * dependent.  Initializes the fail transitions of all states
   * except for the root.
   */
  public void prepare() {
    Queue<State> queue = new LinkedList<State>();
    for (State state : this.root.getCharStateMap().values()) {
      state.setFail(this.root);
      queue.add(state);
    }

    // This is required from this point on, so that root node will return
    // itself when a character does not exist under the root node.
    this.root.setReturnRootIfCharNotExist(true);

    while (!queue.isEmpty()) {
      State currState = queue.remove();

      for (char c : currState.keys()) {
        State failState = currState.getFail();
        State nextStateFail = null;

        // This is probably where most time is consumed in this method.
        while ((nextStateFail = failState.get(c)) == null) {
          failState = failState.getFail();
        }

        State nextState = currState.get(c);
        nextState.setFail(nextStateFail);
        nextState.addOutputs(nextStateFail.getOutputs());
        queue.add(nextState);
      }
    }
    this.isPrepared = true;
  }

  /**
   * Starts a new search, and returns an Iterator of SearchResults.
   */
  public Iterator<SearchResult> search(char[] chars) {
    if (!this.isPrepared)  throw new IllegalStateException(
        "Can't start search until prepare() is called.");
    SearchResult currResult = new SearchResult(this.root, chars, 0);
    SearchResult nextResult = Searcher.continueSearch(currResult);
    return new Searcher(nextResult);
  }

  /**
   * Returns the root of the tree.  Package protected, since the
   * user probably shouldn't touch this.
   */
  protected RootState getRoot() {
    return this.root;
  }
}
