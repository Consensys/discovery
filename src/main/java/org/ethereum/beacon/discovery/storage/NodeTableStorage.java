/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import org.ethereum.beacon.discovery.database.SingleValueSource;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/** Stores {@link NodeTable} and home node info */
public interface NodeTableStorage {
  NodeTable get();

  SingleValueSource<NodeRecordInfo> getHomeNodeSource();

  void commit();
}
