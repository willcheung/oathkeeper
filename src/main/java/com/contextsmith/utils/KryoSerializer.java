package com.contextsmith.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.contextsmith.nlp.ahocorasick.AhoCorasick;
import com.contextsmith.nlp.ahocorasick.RegularState;
import com.contextsmith.nlp.ahocorasick.RootState;
import com.esotericsoftware.kryo.Kryo;

import net.openhft.lang.io.Bytes;
import net.openhft.lang.io.serialization.BytesMarshaller;

public class KryoSerializer<T> implements BytesMarshaller<T> {

  protected static final Logger log = LogManager.getLogger(KryoSerializer.class);

  protected transient static final ThreadLocal<Kryo> KRYOS =
      new ThreadLocal<Kryo>() {
    @Override
    protected Kryo initialValue() {
      Kryo kryo = new Kryo();
      kryo.setRegistrationRequired(true);

      // For mention file.
      kryo.register(MentionValue.class);
      kryo.register(SimpleMentionValue.class);
      kryo.register(ArrayList.class);
      kryo.register(String[].class);

      // For AhoCorasick model.
      kryo.register(AhoCorasick.class);
      kryo.register(RootState.class);
      kryo.register(RegularState.class);
      kryo.register(HashMap.class);
      kryo.register(Object[].class);
      kryo.register(char[].class);
      kryo.register(int[].class);

      return kryo;
    }
  };

  private static final long serialVersionUID = -990236055104301988L;

  private Class<T> valueClass;

  public KryoSerializer(Class<T> tClass) {
    checkNotNull(tClass);
    this.valueClass = tClass;
  }

  public Class<T> getValueClass() {
    return this.valueClass;
  }

  @Override
  public T read(Bytes bytes) {
    checkNotNull(bytes);
    checkNotNull(this.valueClass);
    try {
      return SerializationUtil.deserialize(
          bytes.inputStream(), this.valueClass, KRYOS.get());
    } catch (ClassNotFoundException | IOException e) {
      log.error(e);
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public T read(Bytes bytes, T obj) {
    checkNotNull(bytes);
    // 'obj' will be null for unknown reason.
    return read(bytes);
  }

  @Override
  public void write(Bytes bytes, T obj) {
    checkNotNull(bytes);
    checkNotNull(obj);
    try {
      SerializationUtil.serialize(obj, bytes.outputStream(), KRYOS.get());
    } catch (IOException e) {
      log.error(e);
      e.printStackTrace();
    }
  }

}