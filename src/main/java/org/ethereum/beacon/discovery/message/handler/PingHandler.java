/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class PingHandler implements MessageHandler<PingMessage> {

  private final EnrUpdateTracker enrUpdateTracker;

  public PingHandler(final EnrUpdateTracker enrUpdateTracker) {
    this.enrUpdateTracker = enrUpdateTracker;
  }

  @Override
  public void handle(PingMessage message, NodeSession session) {
    final NodeRecord nodeRecord = session.getHomeNodeRecord();
    final InetSocketAddress remoteAddress = session.getRemoteAddress();

    enrUpdateTracker.updateIfRequired(session, message.getEnrSeq());
    PongMessage responseMessage =
        new PongMessage(
            message.getRequestId(),
            nodeRecord.getSeq(),
            Bytes.wrap(remoteAddress.getAddress().getAddress()),
            remoteAddress.getPort());

    session.sendOutgoingOrdinary(responseMessage);
  }
}
