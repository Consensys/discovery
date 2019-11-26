/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class HashMapDataSource<K, V> implements DataSource<K, V> {

  Map<K, V> store = new ConcurrentHashMap<>();

  @Override
  public Optional<V> get(@Nonnull K key) {
    return Optional.ofNullable(store.get(key));
  }

  @Override
  public void put(@Nonnull K key, @Nonnull V value) {
    store.put(key, value);
  }

  @Override
  public void remove(@Nonnull K key) {
    store.remove(key);
  }

  @Override
  public void flush() {
    // nothing to do
  }

  public Map<K, V> getStore() {
    return store;
  }
}
