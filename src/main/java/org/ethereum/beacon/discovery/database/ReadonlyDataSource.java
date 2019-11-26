/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.util.Optional;
import javax.annotation.Nonnull;

/** Represents read-only {@link DataSource} */
public interface ReadonlyDataSource<KeyType, ValueType> {

  /**
   * Returns the value corresponding to the key.
   *
   * @param key Key in key-value Source
   * @return <code>Optional.empty()</code> if no entry exists
   */
  Optional<ValueType> get(@Nonnull KeyType key);
}
