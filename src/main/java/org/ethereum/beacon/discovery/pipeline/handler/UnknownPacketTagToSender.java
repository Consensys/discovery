/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.UnknownPacket;
import org.ethereum.beacon.discovery.packet5_1.Packet;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.type.Hashes;

/**
 * Assuming we have some unknown packet in {@link Field#PACKET_UNKNOWN}, resolves sender node id
 * using `tag` field of the packet. Next, puts it to the {@link Field#SESSION_LOOKUP} so sender
 * session could be resolved by another handler.
 */
public class UnknownPacketTagToSender implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(UnknownPacketTagToSender.class);
  private final Bytes homeNodeId;
  private final Bytes homeNodeIdHash;

  public UnknownPacketTagToSender(final Bytes nodeId) {
    this.homeNodeId = nodeId;
    this.homeNodeIdHash = Hashes.sha256(nodeId);
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTagToSender, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTagToSender, requirements are satisfied!",
                envelope.getId()));

    Packet<?> packet = (Packet<?>) envelope.get(Field.PACKET);
    envelope.put(Field.SESSION_LOOKUP,
        new SessionLookup(packet.getHeader().getStaticHeader().getSourceNodeId()));

//    ((UnknownPacket) envelope.get(Field.PACKET_UNKNOWN))
//        .getSourceNodeId(homeNodeId, homeNodeIdHash)
//        .ifPresentOrElse(
//            fromNodeId -> envelope.put(Field.SESSION_LOOKUP, new SessionLookup(fromNodeId)),
//            () -> {
//              envelope.put(Field.BAD_PACKET, envelope.get(Field.PACKET_UNKNOWN));
//              envelope.remove(Field.PACKET_UNKNOWN);
//            });
  }
}
