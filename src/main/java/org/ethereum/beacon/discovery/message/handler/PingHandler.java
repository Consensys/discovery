/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class PingHandler implements MessageHandler<PingMessage> {
  @Override
  public void handle(PingMessage message, NodeSession session) {
    final NodeRecord nodeRecord = session.getNodeRecord().orElseThrow();
    PongMessage responseMessage =
        new PongMessage(
            message.getRequestId(),
            nodeRecord.getSeq(),
            ((Bytes) nodeRecord.get(EnrField.IP_V4)), // bytes4
            (int) nodeRecord.get(EnrField.UDP_V4));
    session.sendOutgoing(
        MessagePacket.create(
            session.getHomeNodeId(),
            session.getNodeId(),
            session.getAuthTag().get(),
            session.getInitiatorKey(),
            DiscoveryV5Message.from(responseMessage)));
  }
}
