/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.tuweni.bytes.Bytes;

public class SessionLookup {
  private final Bytes nodeId;

  public SessionLookup(final Bytes nodeId) {
    this.nodeId = nodeId;
  }

  public Bytes getNodeId() {
    return nodeId;
  }
}
