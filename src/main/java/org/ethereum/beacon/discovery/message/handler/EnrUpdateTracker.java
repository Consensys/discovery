/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import org.apache.tuweni.v2.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class EnrUpdateTracker {
  private final EnrUpdater enrUpdater;

  public EnrUpdateTracker(final EnrUpdater enrUpdater) {
    this.enrUpdater = enrUpdater;
  }

  /**
   * If the reported sequence number is greater than the sequence number of the ENR we currently
   * have for the node, triggers a request for the node's latest ENR to update our records.
   *
   * @param session the node's session
   * @param reportedSeqNum the latest sequence number reported by the node (typically from a PING or
   *     PONG message)
   */
  public void updateIfRequired(final NodeSession session, final UInt64 reportedSeqNum) {
    session
        .getNodeRecord()
        .filter(currentRecord -> isUpdateRequired(reportedSeqNum, currentRecord, session))
        .ifPresent(enrUpdater::requestUpdatedEnr);
  }

  private boolean isUpdateRequired(
      final UInt64 reportedSeqNum, final NodeRecord record, final NodeSession session) {
    // Don't request an update if the session isn't yet authenticated. The request message can
    // interfere with the handshake process and we should get the latest ENR as part of that
    // handshake anyway.  If not, the next liveness check will trigger and update anyway.
    return session.isAuthenticated() && record.getSeq().compareTo(reportedSeqNum) < 0;
  }

  public interface EnrUpdater {
    EnrUpdater NOOP = currentRecord -> {};

    void requestUpdatedEnr(NodeRecord currentRecord);
  }
}
