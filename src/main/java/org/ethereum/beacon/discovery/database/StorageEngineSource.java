/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import org.apache.tuweni.bytes.Bytes;

/**
 * Data source supplier based on a specific key-value storage engine like RocksDB, LevelDB, etc.
 *
 * <p>Underlying implementation MUST support batch updates and MAY be aware of open and close
 * operations.
 *
 * @param <ValueType> a value type.
 */
public interface StorageEngineSource<ValueType> extends BatchUpdateDataSource<Bytes, ValueType> {

  /**
   * Opens key-value storage.
   *
   * <p><strong>Note:</strong> an implementation MUST take care of double open calls.
   */
  void open();

  /** Closes key-value storage. */
  void close();
}
