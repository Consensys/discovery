/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.handler;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class SessionLookup {
  private final Optional<NodeRecord> nodeRecord;
  private final Bytes nodeId;

  public SessionLookup(final NodeRecord nodeRecord) {
    this.nodeRecord = Optional.of(nodeRecord);
    this.nodeId = nodeRecord.getNodeId();
  }

  public SessionLookup(final Bytes nodeId) {
    this.nodeRecord = Optional.empty();
    this.nodeId = nodeId;
  }

  public Optional<NodeRecord> getNodeRecord() {
    return nodeRecord;
  }

  public Bytes getNodeId() {
    return nodeId;
  }
}
