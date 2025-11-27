/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.v2.bytes.Bytes;
import org.apache.tuweni.v2.rlp.RLPWriter;
import org.ethereum.beacon.discovery.util.RlpDecodeException;
import org.ethereum.beacon.discovery.util.RlpUtil;

public class EnrFieldInterpreterV4 implements EnrFieldInterpreter {

  public static final Function<Object, Object> DEFAULT_DECODER = Function.identity();
  public static final EnrFieldInterpreterV4 DEFAULT = new EnrFieldInterpreterV4();

  private final Map<String, Function<Object, Object>> fieldDecoders = new HashMap<>();

  @SuppressWarnings({"DefaultCharset"})
  public EnrFieldInterpreterV4() {
    fieldDecoders.put(EnrField.PKEY_SECP256K1, Function.identity());
    fieldDecoders.put(
        EnrField.ID,
        fromBytes(bytes -> IdentitySchema.fromString(new String(bytes.toArrayUnsafe()))));
    fieldDecoders.put(EnrField.IP_V4, Function.identity());
    fieldDecoders.put(EnrField.TCP, fromBytes(bytes -> bytes.toUnsignedBigInteger().intValue()));
    fieldDecoders.put(EnrField.UDP, fromBytes(bytes -> bytes.toUnsignedBigInteger().intValue()));
    fieldDecoders.put(EnrField.QUIC, fromBytes(bytes -> bytes.toUnsignedBigInteger().intValue()));
    fieldDecoders.put(EnrField.IP_V6, Function.identity());
    fieldDecoders.put(EnrField.TCP_V6, fromBytes(bytes -> bytes.toUnsignedBigInteger().intValue()));
    fieldDecoders.put(EnrField.UDP_V6, fromBytes(bytes -> bytes.toUnsignedBigInteger().intValue()));
    fieldDecoders.put(
        EnrField.QUIC_V6, fromBytes(bytes -> bytes.toUnsignedBigInteger().intValue()));
  }

  private static Function<Object, Object> fromBytes(final Function<Bytes, Object> decoder) {
    return value -> {
      if (!(value instanceof Bytes)) {
        throw new RlpDecodeException("Expected Bytes but got " + value.getClass());
      }
      return decoder.apply((Bytes) value);
    };
  }

  @Override
  public Object decode(String key, Object data) {
    Function<Object, Object> fieldDecoder = fieldDecoders.getOrDefault(key, DEFAULT_DECODER);
    return fieldDecoder.apply(data);
  }

  @Override
  public void encode(final RLPWriter writer, final String key, final Object object) {
    encode(writer, object, RlpUtil.MAX_NESTED_LIST_LEVELS);
  }

  private void encode(
      final RLPWriter writer, final Object object, final int remainingNestingLevels) {
    if (object instanceof Bytes) {
      writer.writeValue((Bytes) object);
    } else if (object instanceof BigInteger) {
      writer.writeBigInteger((BigInteger) object);
    } else if (object instanceof Long) {
      writer.writeLong((long) object);
    } else if (object instanceof Integer) {
      writer.writeInt((int) object);
    } else if (object == null) {
      writer.writeByteArray(new byte[0]);
    } else if (object instanceof IdentitySchema) {
      writer.writeString(((IdentitySchema) object).stringName());
    } else if (object instanceof List) {
      if (remainingNestingLevels < 0) {
        throw new RuntimeException("Cannot encode value - max nested list level exceeded");
      }
      writer.writeList(
          (List<?>) object,
          (listWriter, value) -> encode(listWriter, value, remainingNestingLevels - 1));
    } else {
      throw new RuntimeException(
          "Couldn't encode value of type " + object.getClass() + ": no serializer found.");
    }
  }
}
