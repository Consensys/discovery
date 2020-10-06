/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;

/**
 * Assuming we have some unknown packet in {@link Field#PACKET}, resolves sender node id
 * using `tag` field of the packet. Next, puts it to the {@link Field#SESSION_LOOKUP} so sender
 * session could be resolved by another handler.
 */
public class UnknownPacketTagToSender implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(UnknownPacketTagToSender.class);

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.PACKET, envelope)) {
      return;
    }
    if (envelope.contains(Field.SESSION)) {
      return;
    }

    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTagToSender, requirements are satisfied!",
                envelope.getId()));

    Packet<?> packet = envelope.get(Field.PACKET);
    Bytes32 nodeId;
    if (packet instanceof HandshakeMessagePacket) {
      nodeId = ((HandshakeMessagePacket) packet).getHeader().getAuthData().getSourceNodeId();
    } else {
      nodeId = ((OrdinaryMessagePacket) packet).getHeader().getAuthData().getSourceNodeId();
    }
    envelope.put(Field.SESSION_LOOKUP, new SessionLookup(nodeId));
  }
}
