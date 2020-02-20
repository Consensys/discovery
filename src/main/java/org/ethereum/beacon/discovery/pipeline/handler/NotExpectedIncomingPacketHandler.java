/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.RandomPacket;
import org.ethereum.beacon.discovery.packet.UnknownPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Functions;

/** Handles {@link UnknownPacket} from node, which is not on any stage of the handshake with us */
public class NotExpectedIncomingPacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(NotExpectedIncomingPacketHandler.class);

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in NotExpectedIncomingPacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_UNKNOWN, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in NotExpectedIncomingPacketHandler, requirements are satisfied!",
                envelope.getId()));

    UnknownPacket unknownPacket = (UnknownPacket) envelope.get(Field.PACKET_UNKNOWN);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    try {
      // packet it either random or message packet if session is expired
      Bytes authTag = null;
      try {
        RandomPacket randomPacket = unknownPacket.getRandomPacket();
        authTag = randomPacket.getAuthTag();
      } catch (Exception ex) {
        // Not fatal, 1st attempt
      }
      // 2nd attempt
      if (authTag == null) {
        MessagePacket messagePacket = unknownPacket.getMessagePacket();
        authTag = messagePacket.getAuthTag();
      }
      session.setAuthTag(authTag);
      byte[] idNonceBytes = new byte[32];
      Functions.getRandom().nextBytes(idNonceBytes);
      Bytes idNonce = Bytes.wrap(idNonceBytes);
      session.setIdNonce(idNonce);
      WhoAreYouPacket whoAreYouPacket =
          WhoAreYouPacket.createFromNodeId(
              session.getNodeId(),
              authTag,
              idNonce,
              session.getNodeRecord().map(NodeRecord::getSeq).orElse(UInt64.ZERO));
      session.sendOutgoing(whoAreYouPacket);
    } catch (Exception ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              unknownPacket, session.getNodeRecord(), session.getStatus());
      logger.error(error, ex);
      envelope.put(Field.BAD_PACKET, envelope.get(Field.PACKET_UNKNOWN));
      envelope.put(Field.BAD_EXCEPTION, ex);
      envelope.remove(Field.PACKET_UNKNOWN);
      return;
    }
    session.setStatus(NodeSession.SessionStatus.WHOAREYOU_SENT);
    envelope.remove(Field.PACKET_UNKNOWN);
  }
}
