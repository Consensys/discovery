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
  @Override
  public void handle(PingMessage message, NodeSession session) {
    final NodeRecord nodeRecord = session.getNodeRecord().orElseThrow();
    final InetSocketAddress remoteAddress = session.getRemoteAddress();
    PongMessage responseMessage =
        new PongMessage(
            message.getRequestId(),
            nodeRecord.getSeq(),
            Bytes.wrap(remoteAddress.getAddress().getAddress()),
            remoteAddress.getPort());

    session.sendOutgoingOrdinary(responseMessage);
  }
}
