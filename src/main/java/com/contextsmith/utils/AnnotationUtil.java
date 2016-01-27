package com.contextsmith.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.nlp.annotation.Annotation;
import com.google.common.base.Strings;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;

public class AnnotationUtil {

  static final Logger log = LogManager.getLogger(AnnotationUtil.class.getName());

  /**
   * Concatenate annotations separated by "sep",
   * duplicated annotations will be removed.
   * @param anns
   * @param sep
   * @return
   */
  public static String concatAnnotationText(List<Annotation> anns, String sep) {
    if (anns == null) return null;
    Set<String> seen = new HashSet<>();
    StringBuilder sb = new StringBuilder();
    for (Annotation ann : anns){
      String text = ann.getText();
      if (seen.contains(text)) continue;
      if (sb.length() > 0) sb.append(sep);
      sb.append(text);
      seen.add(text);
    }
    return sb.toString();
  }

  public static List<Annotation> filterAnnotationsByType(
      String annotatorType, List<Annotation> annotations, boolean removeFound) {
    List<Annotation> removed = new ArrayList<>();
    for (Iterator<Annotation> iter = annotations.iterator(); iter.hasNext();) {
      Annotation annotation = iter.next();
      if (annotation.getType().equals(annotatorType)) {
        removed.add(annotation);
        if (removeFound) iter.remove();
      }
    }
    return removed;
  }

  // Find the longest spans starting from left to right.
  public static List<Annotation> getLongestSpans(List<Annotation> annotations) {
    // Sort annotation by endByteOffset then string length in descending order.
    sortSpansByOffset(annotations, true);

    List<Annotation> longestSpans = new ArrayList<>();
    int prevStart = Integer.MAX_VALUE;

    for (int i = 0; i < annotations.size(); ++i) {
      Annotation annotation = annotations.get(i);
      if (annotation.getEndOffset() > prevStart) {
        continue;
      }
      longestSpans.add(annotation);
      prevStart = annotation.getBeginOffset();
    }
    Collections.reverse(longestSpans);
    return longestSpans;
  }

  // Set useBeginOffset to false to return a mapping for end-offsets.
  // Returns a mapping from offsets to annotations.
  public static Map<Integer, List<Annotation>> makeOffsetMap(
      List<Annotation> annotations, boolean useBeginOffset) {
    Map<Integer, List<Annotation>> offsetMap = new HashMap<>();
    for (Annotation annotation : annotations) {
      int offset = useBeginOffset ?
          annotation.getBeginOffset() : annotation.getEndOffset();
      List<Annotation> annList = offsetMap.get(offset);
      if (annList == null) {
        annList = new ArrayList<>();
        offsetMap.put(offset, annList);
      }
      annList.add(annotation);
    }
    return offsetMap;
  }

  // Note: Input annotations cannot have overlapping ranges.
  public static RangeMap<Integer, Annotation> makeSpanRange(
      List<Annotation> annotations) {
    RangeMap<Integer, Annotation> rangeAnnMap = TreeRangeMap.create();
    for (int i = 0; i < annotations.size(); ++i) {
      Annotation annotation = annotations.get(i);
      rangeAnnMap.put(annotation.getRange(), annotation);
    }
    return rangeAnnMap;
  }

  /**
   * Merge segments given annotation.
   * @param segments List of tokens got from segmentor: 躁狂 抑郁症 主要 表现
   * @param anns List of annotations 躁狂抑郁症
   * @return Merge tokens based on the annotation result: 躁狂抑郁症 主要 表现
   */
  public static List<Annotation> mergeSegmentsUsingAnns(
      List<Annotation> segments,
      List<Annotation> anns) {
    int annIndex = 0;
    int segIndex = 0;
    List<Annotation> mergedAnns = new ArrayList<>();
    List<Annotation> stack = new ArrayList<>();
    while (annIndex < anns.size() && segIndex < segments.size()) {
      Annotation ann = anns.get(annIndex);
      Annotation seg = segments.get(segIndex);
      int segBegin = seg.getBeginOffset();
      int annBegin = ann.getBeginOffset();
      if (segBegin < annBegin) {
        mergedAnns.add(seg);
        segIndex++;
      } else if (segBegin > annBegin) {
        annIndex++;
      } else {
        // When setBegin == annBegin.
        while (seg.getEndOffset() < ann.getEndOffset()) {
          stack.add(seg);
          seg = segments.get(++segIndex);
        }
        if (seg.getEndOffset() == ann.getEndOffset()) {
          mergedAnns.add(ann);
        } else {
          stack.add(seg);
          mergedAnns.addAll(stack);
        }
        annIndex++;
        segIndex++;
        stack.clear();
      }
    }

    while (segIndex < segments.size()) {
      mergedAnns.add(segments.get(segIndex++));
    }
    return mergedAnns;
  }

  public static void removeAllAnnotationsCoveredByRange(
      Range<Integer> range,
      RangeMap<Integer, Annotation> rangeMap) {
    // Remove the range that covers the begin offset.
    int beginOffset = range.lowerEndpoint();  // inclusively
    if (range.lowerBoundType() == BoundType.OPEN) ++beginOffset;
    Map.Entry<Range<Integer>, Annotation> beginEntry =
        rangeMap.getEntry(beginOffset);
    if (beginEntry != null) rangeMap.remove(beginEntry.getKey());

    // Remove the range that covers the end offset.
    int endOffset = range.upperEndpoint();  // inclusively
    if (range.upperBoundType() == BoundType.OPEN) --endOffset;
    Map.Entry<Range<Integer>, Annotation> endEntry =
        rangeMap.getEntry(endOffset);
    if (endEntry != null) rangeMap.remove(endEntry.getKey());

    // Remove all ranges between begin and end offset.
    rangeMap.remove(range);
  }

  // Sort annotation by byte offset then string length.
  // If isReverse is true, then sort by end offset in descending order,
  // otherwise by begin offset in ascending order.
  public static void sortSpansByOffset(List<Annotation> annotations,
                                       final boolean isReverse) {
    Collections.sort(annotations, new Comparator<Annotation>() {
      @Override
      public int compare(Annotation ann1, Annotation ann2) {
        int r;
        if (isReverse) {  // Sort by endByteOffset from right to left.
          r = Integer.compare(ann2.getEndOffset(), ann1.getEndOffset());
        } else {  // Sort by beginByteOffset from left to right.
          r = Integer.compare(ann1.getBeginOffset(), ann2.getBeginOffset());
        }
        if (r != 0) return r;
        // Sort by annotation length in descending order.
        r = Integer.compare(ann2.length(), ann1.length());
        if (r != 0) return r;
        // Sort by annotation priority in ascending order.
        return Integer.compare(ann1.getPriority(), ann2.getPriority());
      }
    });
  }

  public static String toDebugString(List<Annotation> annotations) {
    StringBuilder builder = new StringBuilder(System.lineSeparator());
    for (Annotation annotation : annotations) {
      builder.append(annotation.toDebugString()).append(System.lineSeparator());
    }
    builder.append(Strings.repeat("-", 80));
    return builder.toString();
  }

/*  public static void mergeConsecutiveSpans(List<Annotation> left,
                                           List<Annotation> right) {
    if (left.isEmpty() || right.isEmpty()) return;

    Map<Integer, Annotation> beginIndexMap = new HashMap<>();
    for (Annotation rightAnn : right) {
      beginIndexMap.put(rightAnn.getBeginOffset(), rightAnn);
    }
    for (Annotation leftAnn: left) {
      Annotation rightAnn = beginIndexMap.get(leftAnn.getEndOffset());
      if (rightAnn == null) continue;
      // Found a direct follower of the left annotation.
      leftAnn.setEndOffset(rightAnn.getEndOffset());
      leftAnn.setText(leftAnn.getText() + rightAnn.getText());
      leftAnn.getQueries().append(rightAnn.getQueries());
    }
  }*/

/*  public static Annotation mergeSpans(String text,
                                      String annotationType,
                                      List<Annotation> annotations) {
    if (annotations == null || annotations.isEmpty() ||
        Strings.isNullOrEmpty(text)) {
      return null;
    }
    // Need at least two annotations to merge.
    if (annotations.size() == 1) return annotations.get(0);

    // Identify if all annotations are noun entities.
    // If so, we treat them differently.
    boolean isAllEntities = true;
    for (Annotation annotation : annotations) {
      if (!annotation.getType().equals(NounAnnotator.getAnnotationType())) {
        isAllEntities = false;
        break;
      }
    }
    // Create a new annotation and adjust its offsets and its content.
    Annotation merged = new Annotation();
    merged.setType(annotationType);
    merged.setBeginOffset(annotations.get(0).getBeginOffset());
    merged.setEndOffset(annotations.get(annotations.size() - 1).getEndOffset());
    merged.setText(text.substring(merged.getBeginOffset(),
                                  merged.getEndOffset()));

    // Now, lets merge the queries based on certain criteria.
    if (isAllEntities) {
      List<String> queries = new ArrayList<>();
      for (Annotation annotation : annotations) {
        queries.add(annotation.getQueries().composeQueryStrings(false));
      }
      merged.setQuery(new QueryLink(
          Joiner.on(ProcessUtil.TSV_DELIMITER).join(queries)));
    } else {
      for (Annotation annotation : annotations) {
        merged.getQueries().append(annotation.getQueries());
      }
    }
    return merged;
  }

  public static List<Annotation> mergeSpansByRE(String text,
                                                Pattern pattern,
                                                String targetAnnType,
                                                Set<String> annTypeToIgnore,
                                                List<Annotation> annotations) {
    // Mapping from begin to end offsets for each annotation.
    Map<Integer, Integer> beginEndOffsetMap = new HashMap<>();

    // Mapping from begin offset to annotation.
    Map<Integer, Annotation> beginAnnotationMap = new HashMap<>();

    // Mapping from each annotation to its position in its input list.
    Map<Annotation, Integer> listIndexMap = new HashMap<>();
    for (int index = 0; index < annotations.size(); ++index) {
      listIndexMap.put(annotations.get(index), index);
    }

    String elementText = flatten(text, annotations, listIndexMap,
                                 annTypeToIgnore, true);
    log.debug(elementText);

    // Merge annotations that match the input pattern.
    Matcher m = pattern.matcher(elementText);
    while (m.find()) {
      // Get annotations in the order specified by the element text.
      List<Annotation> annList = unflatten(m.group(),
                                                          annotations);
      // Merge annotations and its queries.
      Annotation annotation = mergeSpans(text, targetAnnType, annList);

      int beginOffset = listIndexMap.get(annList.get(0));
      int endOffset = listIndexMap.get(annList.get(annList.size() - 1));  // Inclusive
      beginEndOffsetMap.put(beginOffset, endOffset);
      beginAnnotationMap.put(beginOffset, annotation);
    }

    // Reconstruct the list of annotations, with respect to merged annotations.
    List<Annotation> results = new ArrayList<>();
    for (int index = 0; index < annotations.size(); ++index) {
      Annotation merged = beginAnnotationMap.get(index);
      if (merged == null) {
        results.add(annotations.get(index));
      } else {
        results.add(merged);
        // Skip over annotations that were merged.
        index = beginEndOffsetMap.get(index);
      }
    }
    return results;
  }*/
}
