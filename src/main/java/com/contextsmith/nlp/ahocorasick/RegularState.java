package com.contextsmith.nlp.ahocorasick;

import java.util.Arrays;

/**
 * A state represents an element in the Aho-Corasick tree.
 */
public class RegularState extends State {
  private static final char[] EMPTY_CHARS = new char[0];

  // if the map has size 0, keys is null, states is null
  // if the map has size 1, keys is a Character, states is a T
  // else keys is char[] and states is an Object[] of the same size
  // carrying the chars in parallel
  private Object keys = null;
  private Object states = null;

  // BEGIN STATE MAP
  // This is basically an inlined map of chars to states.
  // It is very hacky because it is designed to use absolutely
  // as little space as possible. There be great evil here.
  @Override
  public State get(char key) {
    if (this.keys == null) return null;

    if (this.keys instanceof Character) {
      if (((Character) this.keys).charValue() == key) {
        return (State) this.states;
      } else {
        return null;
      }
    }

    char[] keys = (char[]) this.keys;
    for (int i = 0; i < keys.length; ++i) {
      if (keys[i] == key) {
        return (State) ((Object[]) this.states)[i];
      }
    }
    return null;
  }

  @Override
  public char[] keys() {
    if (this.keys == null) {
      return EMPTY_CHARS;
    } else if (this.keys instanceof Character) {
      return new char[] { ((Character) this.keys).charValue() };
    } else {
      return (char[]) this.keys;
    }
  }

  @Override
  public void put(char key, State value) {
    if (this.keys == null) {
      this.keys = key;
      this.states = value;
      return;
    }

    if (this.keys instanceof Character) {
      if (((Character) this.keys).charValue() == key) {
        this.states = value;
      } else {
        this.keys = new char[] { ((Character) this.keys).charValue(), key };
        this.states = new Object[] { this.states, value };
      }
      return;
    }

    char[] keys = (char[]) this.keys;
    Object[] states = (Object[]) this.states;

    for (int i = 0; i < keys.length; ++i) {
      if (keys[i] == key) {
        states[i] = value;
        return;
      }
    }

    char[] newkeys = Arrays.copyOf(keys, keys.length + 1);
    newkeys[newkeys.length - 1] = key;

    Object[] newstates = Arrays.copyOf(states, states.length + 1);
    newstates[newstates.length - 1] = value;

    this.keys = newkeys;
    this.states = newstates;
  }
}
