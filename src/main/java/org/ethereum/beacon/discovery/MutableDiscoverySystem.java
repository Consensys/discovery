/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import java.util.List;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public interface MutableDiscoverySystem extends DiscoverySystem {
  /**
   * Adds a NodeRecord to the routing table
   *
   * @param nodeRecord The NodeRecord to add to the routing table
   */
  void addNodeRecord(NodeRecord nodeRecord);

  /**
   * Deletes the NodeRecord identified by nodeId from the routing table
   *
   * @param nodeId The node ID to be deleted from the routing table
   */
  void deleteNodeRecord(Bytes nodeId);

  /**
   * Gets all the NodeRecords in the routing table, grouped by their bucket
   *
   * @return all the NodeRecords in the routing table, grouped by their bucket
   */
  List<List<NodeRecord>> getNodeRecordBuckets();
}
