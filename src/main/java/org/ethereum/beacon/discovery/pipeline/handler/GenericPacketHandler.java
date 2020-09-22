/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class GenericPacketHandler implements EnvelopeHandler {

  private static final Logger logger = LogManager.getLogger(GenericPacketHandler.class);

  private final NodeRecordFactory nodeRecordFactory = null;

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
          //          sendWhoAreYou((OrdinaryMessagePacket) packet);
          //          session.setStatus(SessionStatus.WHOAREYOU_SENT);
        } else {
          // TODO error
        }
        break;
      case RANDOM_PACKET_SENT:
        // Should receive WHOAREYOU in answer, not our case
        if (packet instanceof WhoAreYouPacket) {
          envelope.put(Field.PACKET_WHOAREYOU, packet);
          //          sendHandshake((WhoAreYouPacket) packet);
          //          session.setStatus(SessionStatus.AUTHENTICATED);
        } else {
          // TODO error
        }
        break;
      case WHOAREYOU_SENT:
        {
          if (packet instanceof HandshakeMessagePacket) {
            //          HandshakeMessagePacket authPacket = (HandshakeMessagePacket) packet;
            //          processHandshake(authPacket);

            envelope.put(Field.PACKET_AUTH_HEADER_MESSAGE, packet);
            envelope.put(Field.PACKET_MESSAGE, packet);

            //          session.setStatus(SessionStatus.AUTHENTICATED);
            //          processMessagePacket(authPacket);
          } else {
            // TODO error
          }
          break;
        }
      case AUTHENTICATED:
        {
          if (packet instanceof OrdinaryMessagePacket) {
            envelope.put(Field.PACKET_MESSAGE, packet);
            //          processMessagePacket((MessagePacket<?>) packet);
          } else if (packet instanceof WhoAreYouPacket) {
            //          session.setStatus(SessionStatus.RANDOM_PACKET_SENT);
            //          sendHandshake((WhoAreYouPacket) packet);
            //          session.setStatus(SessionStatus.AUTHENTICATED);
            envelope.put(Field.PACKET_WHOAREYOU, packet);
          } else {
            // TODO error
          }

          //        MessagePacket messagePacket = unknownPacket.getMessagePacket();
          //        envelope.put(Field.PACKET_MESSAGE, messagePacket);
          //        envelope.remove(Field.PACKET_UNKNOWN);
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

  private void sendWhoAreYou(OrdinaryMessagePacket srcPacket) {}

  private void sendHandshake(WhoAreYouPacket srcPacket) {}

  private void processHandshake(HandshakeMessagePacket handshakeMessagePacket) {}

  private void processMessagePacket(MessagePacket<?> messagePacket) {}
}
