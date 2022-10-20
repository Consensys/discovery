/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.format;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;

public interface SerializerFactory {

  <T> Function<Bytes, T> getDeserializer(Class<? extends T> objectClass);

  <T> Function<T, Bytes> getSerializer(Class<? extends T> objectClass);
}
