package org.ethereum.beacon.discovery;

import org.apache.tuweni.bytes.Bytes;
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
  void deleteNode(Bytes nodeId);
}
