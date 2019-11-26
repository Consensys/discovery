/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.apache.tuweni.bytes.Bytes;

/** Stores List structure in Source structure */
public class DataSourceList<V> implements HoleyList<V> {
  private static final Bytes SIZE_KEY = Bytes.fromHexString("FFFFFFFFFFFFFFFF");

  private final DataSource<Bytes, Bytes> src;
  private final DataSource<Bytes, V> valSsrc;
  private long size = -1;

  public DataSourceList(
      DataSource<Bytes, Bytes> src,
      @Nonnull final Function<V, Bytes> valueCoder,
      @Nonnull final Function<Bytes, V> valueDecoder) {
    this.src = src;
    valSsrc = new CodecSource.ValueOnly<>(src, valueCoder, valueDecoder);
  }

  @Override
  public void put(long idx, V value) {
    if (value == null) return;
    if (idx >= size()) {
      setSize(idx + 1);
    }
    valSsrc.put(Bytes.minimalBytes(idx), value);
  }

  @Override
  public Optional<V> get(long idx) {
    if (idx < 0 || idx >= size()) return Optional.empty();
    return valSsrc.get(Bytes.minimalBytes(idx));
  }

  @Override
  public long size() {
    if (size < 0) {
      size = src.get(SIZE_KEY).map(Bytes::toLong).orElse(0L);
    }
    return size;
  }

  private void setSize(long newSize) {
    size = newSize;
    src.put(SIZE_KEY, Bytes.minimalBytes(newSize));
  }
}
