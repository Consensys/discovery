/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Set-alike collection with data expiring in configured time. This structure is not thread safe,
 * please, synchronize usage
 *
 * @param <V> data type should extend Comparable<V> to avoid collisions
 */
public class ExpirationSet<V extends Comparable<V>> {

  private final long expirationDelayMillis;
  private final Clock clock;
  private final long size;
  private final TreeSet<Pair<Long, V>> dataExpiration =
      new TreeSet<>(
          Comparator.comparingLong(Pair<Long, V>::getFirst)
              .reversed()
              .thenComparing(Pair::getSecond));
  private final Set<V> data = new HashSet<>();

  /**
   * Creates new set with records expiring in configured timeline after insertion
   *
   * @param expirationDelayMillis Expiration delay, each record will be removed after this time
   * @param clock Clock instance
   * @param size Maximum size of a set, the oldest records will be removed to store new
   */
  public ExpirationSet(final long expirationDelayMillis, final Clock clock, final long size) {
    this.expirationDelayMillis = expirationDelayMillis;
    this.clock = clock;
    if (size < 1) {
      throw new RuntimeException("Minimal size is 1");
    }
    this.size = size;
  }

  /**
   * Creates new set with records expiring in configured timeline after insertion
   *
   * @param expirationDelay Expiration delay, each record will be removed after this time
   * @param clock Clock instance
   * @param size Maximum size of a set, the oldest records will be removed to store new
   */
  public ExpirationSet(final Duration expirationDelay, final Clock clock, final long size) {
    this(expirationDelay.toMillis(), clock, size);
  }

  private void clearExpired() {
    final long currentTime = clock.millis();
    boolean next = true;
    while (next && !dataExpiration.isEmpty()) {
      Pair<Long, V> last = dataExpiration.last();
      if (last.getFirst() < currentTime) {
        dataExpiration.remove(last);
        data.remove(last.getSecond());
      } else {
        next = false;
      }
    }
  }

  public int size() {
    clearExpired();
    return data.size();
  }

  public boolean isEmpty() {
    clearExpired();
    return data.isEmpty();
  }

  public boolean contains(V o) {
    clearExpired();
    return data.contains(o);
  }

  public boolean add(V v) {
    clearExpired();
    // no renewal
    if (contains(v)) {
      return false;
    }
    while (data.size() >= size) {
      Pair<Long, V> last = dataExpiration.last();
      dataExpiration.remove(last);
      data.remove(last.getSecond());
    }
    dataExpiration.add(new Pair<>(clock.millis() + expirationDelayMillis, v));
    data.add(v);
    return true;
  }

  public boolean addAll(@NotNull Collection<? extends V> c) {
    clearExpired();
    c.forEach(this::add);
    return true;
  }

  public void clear() {
    data.clear();
    dataExpiration.clear();
  }
}
