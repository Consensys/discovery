/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Set with data expiring in configured time
 *
 * @param <V> data type
 */
public class ExpirationSet<V> implements Set<V> {

  private final long expirationDelayMillis;
  private final Clock clock;
  private final long size;

  TreeSet<Pair<Long, V>> dataExpiration =
      new TreeSet<>(
          (o1, o2) -> {
            int expirationDeltaSignum = Long.signum(o1.getFirst() - o2.getFirst());
            if (expirationDeltaSignum != 0) {
              return expirationDeltaSignum;
            }
            return Integer.compare(o1.getSecond().hashCode(), o2.getSecond().hashCode());
          });
  Set<V> data = new HashSet<>();

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

  private synchronized void clearExpired() {
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

  @Override
  public int size() {
    clearExpired();
    return data.size();
  }

  @Override
  public boolean isEmpty() {
    clearExpired();
    return data.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    clearExpired();
    return data.contains(o);
  }

  @NotNull
  @Override
  public Iterator<V> iterator() {
    clearExpired();
    return data.iterator();
  }

  @NotNull
  @Override
  public Object[] toArray() {
    clearExpired();
    return data.toArray();
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    throw new RuntimeException("Not implemented yet");
  }

  @Override
  public synchronized boolean add(V v) {
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

  @Override
  public synchronized boolean remove(Object o) {
    throw new RuntimeException("Not implemented yet");
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    return data.containsAll(c);
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends V> c) {
    clearExpired();
    c.forEach(this::add);
    return true;
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new RuntimeException("Not implemented yet");
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new RuntimeException("Not implemented yet");
  }

  @Override
  public synchronized void clear() {
    data.clear();
    dataExpiration.clear();
  }
}
