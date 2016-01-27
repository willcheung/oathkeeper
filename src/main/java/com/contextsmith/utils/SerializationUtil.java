package com.contextsmith.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.base.Stopwatch;

/**
 * A simple class with generic serialize and deserialize method implementations.
 */
public class SerializationUtil {

  protected static final Logger log = LogManager.getLogger(SerializationUtil.class);

  public static <T> T deserialize(byte[] bytes, Class<T> type)
      throws ClassNotFoundException, IOException {
    return deserialize(bytes, type, null);
  }

  public static <T> T deserialize(byte[] bytes, Class<T> type, Kryo kryo)
      throws ClassNotFoundException, IOException {
    return deserialize(new ByteArrayInputStream(bytes), type, kryo);
  }

  public static <T> T deserialize(InputStream is, Class<T> type)
      throws ClassNotFoundException, IOException {
    return deserialize(is, type, null);
  }

  public static <T> T deserialize(InputStream is, Class<T> type, Kryo kryo)
      throws ClassNotFoundException, IOException {
    T object = null;
    if (kryo != null) {
      Input input = new Input(is);
      object = kryo.readObject(input, type);
      input.close();
    } else {  // Java's native deserialization.
      ObjectInputStream ois = new ObjectInputStream(is);
      object = type.cast(ois.readObject());
      ois.close();
    }
    return object;
  }

  public static <T> T deserialize(String filePath, Class<T> type)
      throws ClassNotFoundException, IOException {
    return deserialize(filePath, type, null);
  }

  // Deserialize to Object from the given file.
  public static <T> T deserialize(String filePath, Class<T> type, Kryo kryo)
      throws IOException, ClassNotFoundException {
    log.info("Deserializing \"{}\" ({})...",
             filePath, FileUtil.getReadableFileSize(filePath));
    Stopwatch stopwatch = Stopwatch.createStarted();
    T object = deserialize(new FileInputStream(filePath), type, kryo);
    log.info("Deserialized \"{}\" ({}) in {}. {}",
             filePath, FileUtil.getReadableFileSize(filePath), stopwatch,
             ProcessUtil.getHeapConsumption());
    return object;
  }

  public static void main(String[] args)
      throws IOException, ClassNotFoundException {
    Kryo kryo = new Kryo();
    Double expected = 1.0;
    byte[] bytes = SerializationUtil.serialize(expected, kryo);
    log.info("input: {}, length: {}", expected, bytes.length);
    Double actual =
        SerializationUtil.deserialize(bytes, expected.getClass(), kryo);
    log.info("output: {}", actual);
  }

  public static byte[] serialize(Object object) throws IOException {
    return serialize(object, (Kryo) null);
  }

  public static byte[] serialize(Object object, Kryo kryo) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    serialize(object, baos, kryo);
    return baos.toByteArray();
  }

  public static void serialize(Object object, OutputStream os) throws IOException {
    serialize(object, os, null);
  }

  public static void serialize(Object object, OutputStream os, Kryo kryo)
      throws IOException {
    if (kryo != null) {
      Output output = new Output(os);
      kryo.writeObject(output, object);
      output.close();
    } else {  // Java's native serialization.
      ObjectOutputStream oos = new ObjectOutputStream(os);
      oos.writeObject(object);
      oos.close();
    }
  }

  public static void serialize(Object object, String filePath) throws IOException {
    serialize(object, filePath, null);
  }

  // Serialize the given object and save it to a file.
  public static void serialize(Object object, String filePath, Kryo kryo)
      throws IOException {
    log.info("Serializing \"{}\"...", filePath);
    Stopwatch stopwatch = Stopwatch.createStarted();
    serialize(object, new FileOutputStream(filePath), kryo);
    log.info("Serialized \"{}\" ({}) in {}",
             filePath, FileUtil.getReadableFileSize(filePath), stopwatch);
  }
}
