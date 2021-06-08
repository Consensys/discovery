/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import org.ethereum.beacon.discovery.database.HashMapDataSource;
import org.ethereum.beacon.discovery.database.HoleyList;

/** Creates NodeTableStorage containing NodeTable with indexes */
public class NodeTableStorageImpl implements NodeTableStorage {

  private final NodeTable nodeTable;

  public NodeTableStorageImpl() {
    HoleyList<NodeIndex> nodeIndexesTable = new HoleyList<>();
    this.nodeTable = new NodeTableImpl(new HashMapDataSource<>(), nodeIndexesTable);
  }

  @Override
  public NodeTable get() {
    return nodeTable;
  }
}
