package com.contextsmith.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.io.CharSource;
import com.google.common.io.CharStreams;

public class FileUtil {
  
  static final Logger log = LogManager.getLogger(FileUtil.class);
  
  public static final String COMPRESSED_FILE_RE = ".+?\\.(gz|gzip|zip)";
  
  public static CharSource findResourceAsCharSource(String filename)
      throws IOException {
    return inputStreamToCharSource(findResourceAsStream(filename));
  }

  public static InputStream findResourceAsStream(String filename) {
    InputStream stream = null;

    // First, lookup the file directly.
    File f = new File(filename);
    if (f.exists()) {
      try { stream = new FileInputStream(f); }
      catch (FileNotFoundException e) {}
    }
    if (stream == null) {
      // Second, lookup the file in class-path.
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      stream = classLoader.getResourceAsStream(f.getName());
    }
    if (stream != null) {
      // If this is a compressed file, de-compress it.
      if (filename.matches(COMPRESSED_FILE_RE)) {
        try { stream = new GZIPInputStream(stream); }
        catch (IOException e) {}
      }
    }
    if (stream == null) log.error("Could not locate: {}", filename);
    return stream;
  }

  public static String findResourceAsString(String filename) {
    InputStream stream = findResourceAsStream(filename);
    if (stream == null) return null;
    return readStreamToString(stream);
  }
  
  public static List<String> findResourceAsStringList(String filename) {
    InputStream stream = findResourceAsStream(filename);
    if (stream == null) return null;
    return readStreamToStringList(stream);
  }

  public static CharSource inputStreamToCharSource(
      final InputStream inputStream) throws IOException {
    return new CharSource() {
        @Override
        public Reader openStream() throws IOException {
            return new BufferedReader(new InputStreamReader(inputStream,
                                      StandardCharsets.UTF_8));
        }
    };
  }

  public static Properties loadProperties(String filename) {
    Properties prop = new Properties();
    File file = new File(filename);
    try {
      if (file.exists()) {
        prop.load(new FileInputStream(file));
        return prop;
      }
    } catch (Exception e) { }

    InputStream stream = findResourceAsStream(filename);
    checkNotNull(stream, "Could not find '%s' in classpath.", filename);
    try {
      prop.load(stream);
    }  catch (FileNotFoundException e) {
      log.error("Could not find '{}' in classpath.", filename);
      ProcessUtil.die(e);
    }  catch (IOException e) {
      log.error("Error reading file: {}", filename);
      ProcessUtil.die(e);
    }
    return prop;
  }

  public static String readGzipToString(File filename) {
    String content = null;
    try {
      content = CharStreams.toString(new InputStreamReader(
          new GZIPInputStream(new FileInputStream(filename)),
          StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.error(e);
      e.printStackTrace();
    }
    return content;
  }
  
  public static String readStreamToString(InputStream stream) {
    try {
      return CharStreams.toString(new InputStreamReader(
          stream, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return null;
    }
  }
  
  public static List<String> readStreamToStringList(InputStream stream) {
    try {
      return CharStreams.readLines(new InputStreamReader(
          stream, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return null;
    }
  }
}
