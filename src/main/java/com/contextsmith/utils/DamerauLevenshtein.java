package com.contextsmith.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Damerau-Levenshtein Algorithm is an extension to the Levenshtein
 * Algorithm which solves the edit distance problem between a source string and
 * a target string with the following operations:
 *
 * <ul>
 * <li>Character Insertion</li>
 * <li>Character Deletion</li>
 * <li>Character Replacement</li>
 * <li>Adjacent Character Swap</li>
 * </ul>
 *
 * Note that the adjacent character swap operation is an edit that may be
 * applied when two adjacent characters in the source string match two adjacent
 * characters in the target string, but in reverse order, rather than a general
 * allowance for adjacent character swaps.
 * <p>
 *
 * This implementation allows the client to specify the costs of the various
 * edit operations with the restriction that the cost of two swap operations
 * must not be less than the cost of a delete operation followed by an insert
 * operation. This restriction is required to preclude two swaps involving the
 * same character being required for optimality which, in turn, enables a fast
 * dynamic programming solution.
 * <p>
 *
 * The running time of the Damerau-Levenshtein algorithm is O(n*m) where n is
 * the length of the source string and m is the length of the target string.
 * This implementation consumes O(n*m) space.
 *
 * @author Kevin L. Stern
 */
public class DamerauLevenshtein {

  static final Logger log = LogManager.getLogger(DamerauLevenshtein.class.getName());

  public static DamerauLevenshtein defaultInstance = null;

  public static final int DEFAULT_DELETE_COST = 1;
  public static final int DEFAULT_INSERT_COST = 1;
  public static final int DEFAULT_REPLACE_COST = 2;
  public static final int DEFAULT_SWAP_COST = 1;

  public static DamerauLevenshtein getDefaultInstance() {
    if (defaultInstance == null) {
      defaultInstance = new DamerauLevenshtein();
    }
    return defaultInstance;
  }

  private final int deleteCost;
  private final int insertCost;
  private final int replaceCost;
  private final int swapCost;

  public DamerauLevenshtein() {
    this(DEFAULT_DELETE_COST, DEFAULT_INSERT_COST,
         DEFAULT_REPLACE_COST, DEFAULT_SWAP_COST);
  }

  /**
   * Constructor.
   *
   * @param deleteCost
   *          the cost of deleting a character.
   * @param insertCost
   *          the cost of inserting a character.
   * @param replaceCost
   *          the cost of replacing a character.
   * @param swapCost
   *          the cost of swapping two adjacent characters.
   */
  public DamerauLevenshtein(int deleteCost, int insertCost,
                            int replaceCost, int swapCost) {
    /*
     * Required to facilitate the premise to the algorithm that two swaps of the
     * same character are never required for optimality.
     */
    if (2 * swapCost < insertCost + deleteCost) {
      throw new IllegalArgumentException("Unsupported cost assignment");
    }
    this.deleteCost = deleteCost;
    this.insertCost = insertCost;
    this.replaceCost = replaceCost;
    this.swapCost = swapCost;
  }

  /**
   * Compute the Damerau-Levenshtein distance between the specified source
   * string and the specified target string.
   */
  public int distance(String source, String target) {
    if (source.length() == 0) {
      return target.length() * this.insertCost;
    }
    if (target.length() == 0) {
      return source.length() * this.deleteCost;
    }

    int[][] table = new int[source.length()][target.length()];
    Map<Character, Integer> sourceIndexByCharacter = new HashMap<Character, Integer>();
    if (source.charAt(0) != target.charAt(0)) {
      table[0][0] = Math.min(this.replaceCost, this.deleteCost + this.insertCost);
    }
    sourceIndexByCharacter.put(source.charAt(0), 0);

    for (int i = 1; i < source.length(); i++) {
      int deleteDistance = table[i - 1][0] + this.deleteCost;
      int insertDistance = (i + 1) * this.deleteCost + this.insertCost;
      int matchDistance = i * this.deleteCost
          + (source.charAt(i) == target.charAt(0) ? 0 : this.replaceCost);
      table[i][0] = Math.min(Math.min(deleteDistance, insertDistance),
                             matchDistance);
    }

    for (int j = 1; j < target.length(); j++) {
      int deleteDistance = (j + 1) * this.insertCost + this.deleteCost;
      int insertDistance = table[0][j - 1] + this.insertCost;
      int matchDistance = j * this.insertCost
          + (source.charAt(0) == target.charAt(j) ? 0 : this.replaceCost);
      table[0][j] = Math.min(Math.min(deleteDistance, insertDistance),
                             matchDistance);
    }

    for (int i = 1; i < source.length(); i++) {
      int maxSourceLetterMatchIndex =
          (source.charAt(i) == target.charAt(0)) ? 0 : -1;

      for (int j = 1; j < target.length(); j++) {
        Integer candidateSwapIndex = sourceIndexByCharacter.get(
            target.charAt(j));
        int jSwap = maxSourceLetterMatchIndex;
        int deleteDistance = table[i - 1][j] + this.deleteCost;
        int insertDistance = table[i][j - 1] + this.insertCost;
        int matchDistance = table[i - 1][j - 1];
        if (source.charAt(i) != target.charAt(j)) {
          matchDistance += this.replaceCost;
        } else {
          maxSourceLetterMatchIndex = j;
        }
        int swapDistance;
        if (candidateSwapIndex != null && jSwap != -1) {
          int iSwap = candidateSwapIndex;
          int preSwapCost;
          if (iSwap == 0 && jSwap == 0) {
            preSwapCost = 0;
          } else {
            preSwapCost = table[Math.max(0, iSwap - 1)][Math.max(0, jSwap - 1)];
          }
          swapDistance = preSwapCost + (i - iSwap - 1) * this.deleteCost
              + (j - jSwap - 1) * this.insertCost + this.swapCost;
        } else {
          swapDistance = Integer.MAX_VALUE;
        }
        table[i][j] = Math.min(Math.min(Math.min(deleteDistance, insertDistance),
                      matchDistance), swapDistance);
      }
      sourceIndexByCharacter.put(source.charAt(i), i);
    }
    return table[source.length() - 1][target.length() - 1];
  }

  public double getSimilarity(String s1, String s2) {
    String longer = s1;
    String shorter = s2;
    if (s2.length() > s1.length()) {
      longer = s2;
      shorter = s1;
    }
    int minReplaceCost = Math.min(this.deleteCost + this.insertCost,
                                  this.replaceCost);
    int maxScore = shorter.length() * minReplaceCost
                 + (longer.length() - shorter.length()) * this.insertCost;
    return 1 - ((double) distance(s1, s2) / maxScore);
  }
}