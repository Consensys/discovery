/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecryptException;

/** Handles {@link MessagePacket} in {@link Field#PACKET_MESSAGE} field */
public class MessagePacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(MessagePacketHandler.class);
  private final NodeRecordFactory nodeRecordFactory;

  public MessagePacketHandler(NodeRecordFactory nodeRecordFactory) {
    this.nodeRecordFactory = nodeRecordFactory;
  }

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.PACKET_MESSAGE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.MASKING_IV, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessagePacketHandler, requirements are satisfied!",
                envelope.getId()));

    MessagePacket<?> packet = (MessagePacket<?>) envelope.get(Field.PACKET_MESSAGE);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);

    try {
      Bytes16 maskingIV = (Bytes16) envelope.get(Field.MASKING_IV);
      V5Message message =
          packet.decryptMessage(maskingIV, session.getRecipientKey(), nodeRecordFactory);
      envelope.put(Field.MESSAGE, message);
      envelope.remove(Field.PACKET_MESSAGE);
    } catch (DecryptException e) {
      logger.trace(
          () ->
              String.format(
                  "Failed to decrypt message [%s] from node %s in status %s. Will be sending WHOAREYOU...",
                  packet, session.getNodeRecord(), session.getState()));

      envelope.remove(Field.PACKET_MESSAGE);
      envelope.put(Field.UNAUTHORIZED_PACKET_MESSAGE, packet);
    } catch (Exception ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getState());
      logger.debug(error, ex);
      envelope.remove(Field.PACKET_MESSAGE);
      envelope.put(Field.BAD_PACKET, packet);
    }
  }
}
