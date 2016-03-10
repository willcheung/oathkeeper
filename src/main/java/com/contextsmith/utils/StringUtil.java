package com.contextsmith.utils;

import java.text.BreakIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.primitives.Ints;

public class StringUtil {

  public static int indexOfAny(String str, Set<Character> searchChars,
                               int startPos) {
    if (StringUtils.isEmpty(str) || startPos >= str.length() || startPos < 0 ||
        searchChars == null || searchChars.isEmpty()) {
      return StringUtils.INDEX_NOT_FOUND;
    }
    for (int i = startPos; i < str.length(); ++i) {
      if (searchChars.contains(str.charAt(i))) return i;
    }
    return StringUtils.INDEX_NOT_FOUND;
  }

  public static int lastIndexOfAny(String str, Set<Character> searchChars,
                                   int endPos) {
    if (StringUtils.isEmpty(str) || endPos > str.length() || endPos <= 0 ||
        searchChars == null || searchChars.isEmpty()) {
      return StringUtils.INDEX_NOT_FOUND;
    }
    for (int i = endPos - 1; i >= 0; --i) {
      if (searchChars.contains(str.charAt(i))) return i;
    }
    return StringUtils.INDEX_NOT_FOUND;
  }

  public static Integer parseLeadingIntegers(String s) {
    Matcher m = Pattern.compile("-?[0-9]+").matcher(s);
    if (m.find()) return Ints.tryParse(m.group());
    return null;
  }

  public static String substringFromLast(String s, int maxLength) {
    return s.substring(Math.max(0, s.length() - maxLength));
  }

  public static String substringFromStart(String s, int maxLength) {
    return s.substring(0, Math.min(s.length(), maxLength));
  }

  public static String truncateAtWordBoundary(String text, int maxChars) {
    if (text.length() < maxChars) return text;
    BreakIterator bi = BreakIterator.getWordInstance();
    bi.setText(text);
    int firstBeforeOffset = bi.preceding(maxChars);
    return text.substring(0, firstBeforeOffset).trim() + "...";
  }
}
