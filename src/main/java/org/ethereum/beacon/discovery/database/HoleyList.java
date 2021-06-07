/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Add-only list which can miss elements at some positions and its size is the maximal element index
 * Also can be treated as <code>Map&lt;Long, V&gt;</code> with maximal key tracking.
 */
public class HoleyList<V> {

  private final Map<Long, V> data = new HashMap<>();

  private long size = 0;

  public void put(long idx, V value) {
    if (value == null) return;
    if (idx >= size()) {
      setSize(idx + 1);
    }
    data.put(idx, value);
  }

  /**
   * Returns element at index <code>idx</code> Empty instance is returned if no element with this
   * index
   */
  public Optional<V> get(long idx) {
    if (idx < 0 || idx >= size()) return Optional.empty();
    return Optional.ofNullable(data.get(idx));
  }

  /** Maximal index of inserted element + 1 */
  public long size() {
    return size;
  }

  private void setSize(long newSize) {
    size = newSize;
  }

  /** Puts element with index <code>size()</code> */
  public void add(V value) {
    put(size(), value);
  }
}
