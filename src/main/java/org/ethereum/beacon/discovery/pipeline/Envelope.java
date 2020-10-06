/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Container for any kind of objects used in packet-messages-tasks flow */
public class Envelope {
  private final UUID id;
  private final Map<Field<?>, Object> data = new HashMap<>();

  public Envelope() {
    this.id = UUID.randomUUID();
  }

  public synchronized <T> void put(Field<T> key, T value) {
    data.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public synchronized <T> T get(Field<T> key) {
    return (T) data.get(key);
  }

  public synchronized boolean remove(Field<?> key) {
    return data.remove(key) != null;
  }

  public synchronized boolean contains(Field<?> key) {
    return data.containsKey(key);
  }

  public UUID getId() {
    return id;
  }
}
