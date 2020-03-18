/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.ethereum.beacon.discovery.schema.NodeSession.SessionStatus.AUTHENTICATED;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Functions;

/** Handles {@link AuthHeaderMessagePacket} in {@link Field#PACKET_AUTH_HEADER_MESSAGE} field */
public class AuthHeaderMessagePacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(AuthHeaderMessagePacketHandler.class);
  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;
  private final NodeRecordFactory nodeRecordFactory;

  public AuthHeaderMessagePacketHandler(
      Pipeline outgoingPipeline, Scheduler scheduler, NodeRecordFactory nodeRecordFactory) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
    this.nodeRecordFactory = nodeRecordFactory;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in AuthHeaderMessagePacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_AUTH_HEADER_MESSAGE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in AuthHeaderMessagePacketHandler, requirements are satisfied!",
                envelope.getId()));

    AuthHeaderMessagePacket packet =
        (AuthHeaderMessagePacket) envelope.get(Field.PACKET_AUTH_HEADER_MESSAGE);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    try {
      packet.decodeEphemeralPubKey();
      Bytes ephemeralPubKey = packet.getEphemeralPubkey();
      Functions.HKDFKeys keys =
          Functions.hkdf_expand(
              session.getNodeId(),
              session.getHomeNodeId(),
              session.getStaticNodeKey(),
              ephemeralPubKey,
              session.getIdNonce());
      // Swap keys because we are not initiator, other side is
      session.setInitiatorKey(keys.getRecipientKey());
      session.setRecipientKey(keys.getInitiatorKey());
      packet.decodeMessage(session.getRecipientKey(), keys.getAuthResponseKey(), nodeRecordFactory);
      if (packet.getNodeRecord() != null && !packet.getNodeRecord().isValid()) {
        logger.info(
            String.format(
                "Node record not valid for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getStatus()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      final NodeRecord nodeRecord = session.getNodeRecord().orElseGet(packet::getNodeRecord);
      // Check the node record matches the ID we expect
      if (nodeRecord == null || !nodeRecord.getNodeId().equals(session.getNodeId())) {
        logger.info(
            String.format(
                "Incorrect node ID for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getStatus()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      if (!packet.isValid(session.getIdNonce(), (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1))) {
        logger.info(
            String.format(
                "Packet verification not passed for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getStatus()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      envelope.put(Field.MESSAGE, packet.getMessage());
      if (packet.getNodeRecord() != null) {
        session.updateNodeRecord(packet.getNodeRecord());
        session.getNodeTable().save(NodeRecordInfo.createDefault(nodeRecord));
      }
    } catch (Exception ex) {
      logger.debug(
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus()),
          ex);
      markHandshakeAsFailed(envelope, session);
      return;
    }
    session.setStatus(AUTHENTICATED);
    envelope.remove(Field.PACKET_AUTH_HEADER_MESSAGE);
    NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
  }

  private void markHandshakeAsFailed(final Envelope envelope, final NodeSession session) {
    envelope.remove(Field.PACKET_AUTH_HEADER_MESSAGE);
    session.cancelAllRequests("Failed to handshake");
  }
}
