 package com.contextsmith.demo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {

  public static final String LATIN_CHARS_RE_NAME = "[A-Za-z]";
  
  public static String extractLatinChars(String text) {
    Matcher m = Pattern.compile(LATIN_CHARS_RE_NAME).matcher(text);
    StringBuffer buf = new StringBuffer();
    while (m.find()) {
      if (buf.length() > 0) buf.append(" ");
      buf.append(m.group());
    }
    return buf.toString();
  }
  
  public static int countCharacters(String s, Pattern p) {
    int count = 0;
    Matcher m = p.matcher(s);
    while (m.find()) ++count;
    return count;
  }
  
  public static int countLines(String str) {
    String[] lines = str.split("\r\n|\r|\n");
    return  lines.length;
 }
  
  public static String replaceNewlines(String str) {
    str = str.replaceAll("\\r?\\n", "");
    return str;
  }
}
