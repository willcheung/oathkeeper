package com.contextsmith.utils;

public class StringUtil {

  public static String substringFromLast(String s, int maxLength) {
    return s.substring(Math.max(0, s.length() - maxLength));
  }

  public static String substringFromStart(String s, int maxLength) {
    return s.substring(0, Math.min(s.length(), maxLength));
  }
}
