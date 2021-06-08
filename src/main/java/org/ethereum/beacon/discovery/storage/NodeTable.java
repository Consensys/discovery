/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/**
 * Stores Ethereum Node Records in {@link NodeRecordInfo} containers. Also stores home node as node
 * record.
 */
public interface NodeTable {
  void save(NodeRecordInfo node);

  void remove(NodeRecordInfo node);

  Optional<NodeRecordInfo> getNode(Bytes nodeId);

  /** Returns stream of nodes including `nodeId` (if it's found) in logLimit distance from it. */
  Stream<NodeRecordInfo> streamClosestNodes(Bytes nodeId, int logLimit);

  /** Returns list of nodes including `nodeId` (if it's found) in logLimit distance from it. */
  List<NodeRecordInfo> findClosestNodes(Bytes nodeId, int logLimit);
}
