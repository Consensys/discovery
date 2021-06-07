/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.database.DataSource;
import org.ethereum.beacon.discovery.database.HashMapDataSource;
import org.ethereum.beacon.discovery.database.HoleyList;
import org.ethereum.beacon.discovery.database.SingleValueSource;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/** Creates NodeTableStorage containing NodeTable with indexes */
public class NodeTableStorageImpl implements NodeTableStorage {

  private final SingleValueSource<NodeRecordInfo> homeNodeSource;
  private final NodeTable nodeTable;

  public NodeTableStorageImpl() {
    DataSource<Bytes, NodeRecordInfo> nodeTableSource = new HashMapDataSource<>();

    HoleyList<NodeIndex> nodeIndexesTable = new HoleyList<>();
    this.homeNodeSource = SingleValueSource.memSource();
    this.nodeTable = new NodeTableImpl(nodeTableSource, nodeIndexesTable, homeNodeSource);
  }

  @Override
  public NodeTable get() {
    return nodeTable;
  }

  @Override
  public SingleValueSource<NodeRecordInfo> getHomeNodeSource() {
    return homeNodeSource;
  }
}
