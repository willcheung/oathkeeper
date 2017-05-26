package com.contextsmith.utils;

import java.lang.reflect.Type;
import java.text.BreakIterator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.internet.InternetAddress;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.primitives.Ints;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class StringUtil {
  public static final String EN_STOPWORD_PATH = "en-stopwords.txt";
  private static final Logger log = LoggerFactory.getLogger(StringUtil.class);
  private static Set<String> enStopwords = null;
  private static Gson gson = null;
  private static ObjectMapper jackson = null;

  public static synchronized Gson getGsonInstance() {
    if (gson == null) gson = makeGsonInstance();
    return gson;
  }

  public static synchronized ObjectMapper getJacksonInstance() {
    if (jackson == null) {
      jackson = new ObjectMapper();
      jackson.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }
    return jackson;
  }

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

  public static boolean isEnStopword(String token) {
    if (enStopwords == null) {
      enStopwords = loadStopwords(EN_STOPWORD_PATH);
    }
    return enStopwords.contains(token.trim().toLowerCase());
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

  public static Pattern makeLookupPattern(List<String> strings,
                                          boolean caseInsensitive) {
    StringBuilder builder = new StringBuilder();
    for (String string : strings) {
      string = string.trim();
      if (StringUtils.isBlank(string)) continue;
      string = string.replaceAll("\\s+", "\\\\E\\\\b.{1,2}\\\\b\\\\Q");
      if (builder.length() != 0) builder.append("|");
      builder.append("\\Q" + string + "\\E");
    }
    String regex = String.format("\\b(?:%s)\\b", builder.toString());
    if (caseInsensitive) regex = "(?i)" + regex;
    log.debug("Search pattern: {}", regex);
    return Pattern.compile(regex);
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

  public static String toJson(Object o) {
    return getGsonInstance().toJson(o);
  }

  public static List<String> tokenizeQuery(String query, int minLength) {
    List<String> tokens = new ArrayList<>();
    Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(query);
    while (m.find()) {
      String token = m.group(1).replace("\"", "");
      if (token.length() < minLength || isEnStopword(token)) continue;
      tokens.add(token);
    }
    return tokens;
  }

  public static String truncateAtWordBoundary(String text, int maxChars) {
    if (text.length() < maxChars) return text;
    BreakIterator bi = BreakIterator.getWordInstance();
    bi.setText(text);
    int firstBeforeOffset = bi.preceding(maxChars);
    return text.substring(0, firstBeforeOffset).trim() + "...";
  }

  private static Set<String> loadStopwords(String filename) {
    log.info("Loading stopwords from: {}", filename);
    List<String> lines = FileUtil.findResourceAsStringList(filename);
    if (lines == null) {
      log.error("Error reading stopwords from: {}", filename);
      return null;
    }
    Set<String> stopwords = new HashSet<>();
    for (String line : lines) {
      stopwords.add(line.trim().toLowerCase());
    }
    return stopwords;
  }

  private static Gson makeGsonInstance() {
    JsonSerializer<Date> dateSerializer = new JsonSerializer<Date>() {
      @Override
      public JsonElement serialize(Date src, Type typeOfSrc,
                                   JsonSerializationContext context) {
        return (src == null) ? null : new JsonPrimitive(src.getTime() / 1000);
      }
    };

    JsonDeserializer<Date> dateDeserializer = new JsonDeserializer<Date>() {
      @Override
      public Date deserialize(JsonElement json, Type typeOfT,
                              JsonDeserializationContext context)
          throws JsonParseException {
        return (json == null) ? null : new Date(json.getAsLong() * 1000);
      }
    };

    JsonSerializer<ZonedDateTime> zonedDateTimeSerializer =
        new JsonSerializer<ZonedDateTime>() {
      @Override
      public JsonElement serialize(ZonedDateTime src, Type typeOfSrc,
                                   JsonSerializationContext context) {
        return (src == null) ? null : new JsonPrimitive(
            src.toInstant().toEpochMilli() / 1000);
      }
    };

    JsonDeserializer<ZonedDateTime> zonedDateTimeDeserializer =
        new JsonDeserializer<ZonedDateTime>() {
      @Override
      public ZonedDateTime deserialize(JsonElement json, Type typeOfT,
                                       JsonDeserializationContext context)
          throws JsonParseException {
        return (json == null) ? null : ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(json.getAsLong()), ZoneId.systemDefault());
      }
    };

    ExclusionStrategy exclusionStrat = new ExclusionStrategy() {
      @Override
      public boolean shouldSkipClass(Class<?> clazz) {
        return false;
      }

      @Override
      public boolean shouldSkipField(FieldAttributes fa) {
        return fa.getDeclaringClass() == InternetAddress.class &&
               fa.getName().equals("encodedPersonal");
      }
    };

    return new GsonBuilder().setExclusionStrategies(exclusionStrat)
                            .registerTypeAdapter(Date.class, dateSerializer)
                            .registerTypeAdapter(Date.class, dateDeserializer)
                            .registerTypeAdapter(ZonedDateTime.class, zonedDateTimeSerializer)
                            .registerTypeAdapter(ZonedDateTime.class, zonedDateTimeDeserializer)
                            .disableHtmlEscaping()
                            .create();
  }
}
