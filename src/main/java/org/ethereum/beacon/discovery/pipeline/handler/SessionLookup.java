/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.tuweni.bytes.Bytes;

public class SessionLookup {
  private final Bytes nodeId;
  private final Runnable onMissingSession;

  public SessionLookup(final Bytes nodeId, final Runnable onMissingSession) {
    this.nodeId = nodeId;
    this.onMissingSession = onMissingSession;
  }

  public Bytes getNodeId() {
    return nodeId;
  }

  public void onMissingSession() {
    onMissingSession.run();
  }
}
