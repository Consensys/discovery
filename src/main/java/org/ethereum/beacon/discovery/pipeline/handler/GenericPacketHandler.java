/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class GenericPacketHandler implements EnvelopeHandler {

  private static final Logger logger = LogManager.getLogger(GenericPacketHandler.class);

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTypeByStatus, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.PACKET, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in UnknownPacketTypeByStatus, requirements are satisfied!",
                envelope.getId()));

    Packet<?> packet = (Packet<?>) envelope.get(Field.PACKET);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);

    switch (session.getStatus()) {
      case INITIAL:
        if (packet instanceof OrdinaryMessagePacket) {
          envelope.put(Field.PACKET_MESSAGE, packet);
        } else {
          // TODO error
        }
        break;
      case RANDOM_PACKET_SENT:
        // Should receive WHOAREYOU in answer, not our case
        if (packet instanceof WhoAreYouPacket) {
          envelope.put(Field.PACKET_WHOAREYOU, packet);
        } else {
          // TODO error
        }
        break;
      case WHOAREYOU_SENT:
        {
          if (packet instanceof HandshakeMessagePacket) {
            envelope.put(Field.PACKET_AUTH_HEADER_MESSAGE, packet);
            envelope.put(Field.PACKET_MESSAGE, packet);
          } else {
            // TODO error
          }
          break;
        }
      case AUTHENTICATED:
        {
          if (packet instanceof OrdinaryMessagePacket) {
            envelope.put(Field.PACKET_MESSAGE, packet);
          } else if (packet instanceof WhoAreYouPacket) {
            envelope.put(Field.PACKET_WHOAREYOU, packet);
          } else {
            // TODO error
          }
          break;
        }
      default:
        {
          String error =
              String.format(
                  "Not expected status: %s from node: %s",
                  session.getStatus(), session.getNodeRecord());
          logger.error(error);
          throw new RuntimeException(error);
        }
    }
  }
}
