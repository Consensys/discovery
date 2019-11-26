/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import org.ethereum.beacon.discovery.type.Hashes;

/** In memory database implementation based on {@link HashMapDataSource}. */
public class InMemoryDatabase extends XorKeyDatabase {

  public InMemoryDatabase() {
    super(new HashMapDataSource<>(), Hashes::sha256);
  }

  @Override
  public void commit() {}

  @Override
  public void close() {}
}
