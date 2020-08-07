/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.UnknownPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;

/**
 * Assuming we have some unknown packet in {@link Field#PACKET_UNKNOWN}, resolves sender node id
 * using `tag` field of the packet. Next, puts it to the {@link Field#SESSION_LOOKUP} so sender
 * session could be resolved by another handler.
 */
public class UnknownPacketTagToSender implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(UnknownPacketTagToSender.class);
  private final Bytes homeNodeId;

  public UnknownPacketTagToSender(final Bytes nodeId) {
    this.homeNodeId = nodeId;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTagToSender, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_UNKNOWN, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTagToSender, requirements are satisfied!",
                envelope.getId()));

    if (!envelope.contains(Field.PACKET_UNKNOWN)) {
      return;
    }
    ((UnknownPacket) envelope.get(Field.PACKET_UNKNOWN))
        .getSourceNodeId(homeNodeId)
        .ifPresentOrElse(
            fromNodeId -> envelope.put(Field.SESSION_LOOKUP, new SessionLookup(fromNodeId)),
            () -> {
              envelope.put(Field.BAD_PACKET, envelope.get(Field.PACKET_UNKNOWN));
              envelope.remove(Field.PACKET_UNKNOWN);
            });
  }
}
