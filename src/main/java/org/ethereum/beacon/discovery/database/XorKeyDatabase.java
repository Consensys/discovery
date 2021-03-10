/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;

/** An abstract class that uses {@link XorDataSource} for storage multiplexing. */
public abstract class XorKeyDatabase implements Database {

  private final DataSource<Bytes, Bytes> backingDataSource;
  private final Function<Bytes, Bytes> sourceNameHasher;

  XorKeyDatabase(
      DataSource<Bytes, Bytes> backingDataSource, Function<Bytes, Bytes> sourceNameHasher) {
    this.backingDataSource = backingDataSource;
    this.sourceNameHasher = sourceNameHasher;
  }

  @Override
  @SuppressWarnings({"DefaultCharset"})
  public DataSource<Bytes, Bytes> createStorage(String name) {
    return new XorDataSource<>(
        backingDataSource, sourceNameHasher.apply(Bytes.wrap(name.getBytes())));
  }

  public DataSource<Bytes, Bytes> getBackingDataSource() {
    return backingDataSource;
  }
}
