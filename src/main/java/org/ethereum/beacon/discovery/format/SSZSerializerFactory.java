/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.format;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;

public class SSZSerializerFactory implements SerializerFactory {

  private final SSZSerializerFactory serializer;

  public SSZSerializerFactory(SSZSerializerFactory serializer) {
    this.serializer = serializer;
  }

  @Override
  public <T> Function<Bytes, T> getDeserializer(Class<? extends T> objectClass) {
    return bytes -> serializer.getDeserializer(objectClass).apply(bytes);
  }

  @Override
  public <T> Function<T, Bytes> getSerializer(Class<? extends T> objectClass) {
    return serializer.getSerializer(objectClass);
  }
}
