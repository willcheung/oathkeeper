package com.contextsmith.nlp.ahocorasick;

import java.util.HashMap;
import java.util.Map;

import com.google.common.primitives.Chars;

/**
 * A state represents an element in the Aho-Corasick tree.
 */
public class RootState extends State {
  private final Map<Character, State> charStateMap;
  private boolean returnRootIfCharNotExist;

  public RootState() {
    this.charStateMap = new HashMap<Character, State>();
    this.returnRootIfCharNotExist = false;
  }

  @Override
  public State get(char key) {
    State state = this.charStateMap.get(key);
    if (this.returnRootIfCharNotExist && state == null) {
      return this;
    }
    return state;
  }

  public Map<Character, State> getCharStateMap() {
    return this.charStateMap;
  }

  @Override
  public char[] keys() {
    return Chars.toArray(this.charStateMap.keySet());
  }

  @Override
  public void put(char key, State value) {
    this.charStateMap.put(key, value);
  }

  public void setReturnRootIfCharNotExist(boolean returnRoot) {
    this.returnRootIfCharNotExist = returnRoot;
  }
}
