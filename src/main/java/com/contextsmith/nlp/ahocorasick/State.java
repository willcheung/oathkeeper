package com.contextsmith.nlp.ahocorasick;

import java.util.Arrays;

public abstract class State {

  // Same story. here's an inlined set of ints backed by an array of ints.
  private static final int[] EMPTY_INTS = new int[0];

  // null when empty
  // an Integer when size 1
  // an int[] when size > 1
  private Object outputs = null;
  private State fail = null;

  public void addOutput(int value) {
    if (this.outputs == null) {
      this.outputs = value;
    } else if (this.outputs instanceof Integer) {
      int v = ((Integer) this.outputs).intValue();
      if (value != v) {
        this.outputs = new int[] {v, value};
      }
    } else {
      int[] outputs = (int[]) this.outputs;
      for (int v : outputs) {
        if (v == value) return;
      }
      int[] newoutputs = Arrays.copyOf(outputs, outputs.length + 1);
      newoutputs[newoutputs.length - 1] = value;
      this.outputs = newoutputs;
    }
  }

  public void addOutputs(int[] values) {
    for (int i : values) {
      this.addOutput(i);
    }
  }

  public State extend(char b) {
    State s = this.get(b);
    if (s != null) return s;
    State nextState = new RegularState();
    this.put(b, nextState);
    return nextState;
  }

  public State extendAll(char[] chars) {
    State state = this;  // Start extending from this state.
    for (int i = 0; i < chars.length; ++i) {
      State s = state.get(chars[i]);
      state = (s == null) ? state.extend(chars[i]) : s;
    }
    return state;
  }

  public State get(char key) {
    throw new UnsupportedOperationException("Not implemented yet!");
  }

  public State getFail() {
    return this.fail;
  }

  public int[] getOutputs() {
    if (this.outputs == null) {
      return EMPTY_INTS;
    } else if (this.outputs instanceof Integer) {
      return new int[] { ((Integer) this.outputs).intValue() };
    } else {
      return (int[]) this.outputs;
    }
  }

  public char[] keys() {
    throw new UnsupportedOperationException("Not implemented yet!");
  }

  public void put(char key, State value) {
    throw new UnsupportedOperationException("Not implemented yet!");
  }

  public void setFail(State fail) {
    this.fail = fail;
  }

  /**
   * Returns the size of the tree rooted at this State.
   * Note: do not call this if there are loops in the edgelist graph, such as
   * those introduced by AhoCorasick.prepare().
   */
  public int size() {
    char[] keys = this.keys();
    int result = 1;
    for (int i = 0; i < keys.length; ++i) {
      result += this.get(keys[i]).size();
    }
    return result;
  }
}
