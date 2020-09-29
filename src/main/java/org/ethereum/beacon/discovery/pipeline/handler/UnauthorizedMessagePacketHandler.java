/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.schema.NodeSession.SessionState;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.util.Functions;

public class UnauthorizedMessagePacketHandler implements EnvelopeHandler {

  private static final Logger logger = LogManager.getLogger(UnauthorizedMessagePacketHandler.class);

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in NotExpectedIncomingPacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.UNAUTHORIZED_PACKET_MESSAGE, envelope)) {
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

    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    OrdinaryMessagePacket unknownPacket =
        (OrdinaryMessagePacket) envelope.get(Field.UNAUTHORIZED_PACKET_MESSAGE);
    try {
      // packet it either random or message packet if session is expired
      Bytes12 msgNonce = unknownPacket.getHeader().getAuthData().getAesGcmNonce();
      session.setAuthTag(msgNonce);
      Bytes32 idNonce = Bytes32.random(Functions.getRandom());
      session.setIdNonce(idNonce);

      Header<WhoAreYouAuthData> header = Header.createWhoAreYouHeader(
          session.getHomeNodeId(),
          msgNonce,
          idNonce,
          session.getNodeRecord().map(NodeRecord::getSeq).orElse(UInt64.ZERO));
      WhoAreYouPacket whoAreYouPacket = WhoAreYouPacket.create(header);
      session.sendOutgoing(whoAreYouPacket);

      session.setState(SessionState.WHOAREYOU_SENT);
    } catch (Exception ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              unknownPacket, session.getNodeRecord(), session.getState());
      logger.debug(error, ex);
      envelope.put(Field.BAD_PACKET, envelope.get(Field.PACKET_MESSAGE));
      envelope.put(Field.BAD_EXCEPTION, ex);
    }
    envelope.remove(Field.PACKET_MESSAGE);
  }
}
