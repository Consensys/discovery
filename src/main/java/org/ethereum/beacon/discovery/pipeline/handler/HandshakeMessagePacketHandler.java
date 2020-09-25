/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.ID_SIGNATURE_PREFIX;
import static org.ethereum.beacon.discovery.schema.NodeSession.SessionStatus.AUTHENTICATED;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
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
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.Functions;

/** Handles {@link HandshakeMessagePacket} in {@link Field#PACKET_AUTH_HEADER_MESSAGE} field */
public class HandshakeMessagePacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(HandshakeMessagePacketHandler.class);
  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;
  private final NodeRecordFactory nodeRecordFactory;

  public HandshakeMessagePacketHandler(
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

    HandshakeMessagePacket packet =
        (HandshakeMessagePacket) envelope.get(Field.PACKET_AUTH_HEADER_MESSAGE);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    try {
      Bytes ephemeralPubKey = packet.getHeader().getAuthData().getEphemeralPubKey();
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

      Optional<NodeRecord> enr = packet.getHeader().getAuthData().getNodeRecord(nodeRecordFactory);
      if (!enr.map(NodeRecord::isValid).orElse(true)) {
        logger.info(
            String.format(
                "Node record not valid for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getStatus()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      final Optional<NodeRecord> nodeRecordMaybe = session.getNodeRecord().or(() -> enr);
      // Check the node record matches the ID we expect
      if (!nodeRecordMaybe.map(r -> r.getNodeId().equals(session.getNodeId())).orElse(false)) {
        logger.info(
            String.format(
                "Incorrect node ID for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getStatus()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      NodeRecord nodeRecord = nodeRecordMaybe.get();

      Bytes idSignatureInput =
          CryptoUtil.sha256(Bytes.wrap(ID_SIGNATURE_PREFIX, session.getIdNonce(), ephemeralPubKey));

      if (!Functions.verifyECDSASignature(
          packet.getHeader().getAuthData().getIdSignature(),
          idSignatureInput,
          (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1))) {
        logger.info(
            String.format(
                "ID signature not valid for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getStatus()));
        markHandshakeAsFailed(envelope, session);
        return;
      }

      V5Message message = packet.decryptMessage(session.getRecipientKey(), nodeRecordFactory);
      envelope.put(Field.MESSAGE, message);

      enr.ifPresent(
          r -> {
            session.updateNodeRecord(r);
            session.getNodeTable().save(NodeRecordInfo.createDefault(r));
          });

      session.setStatus(AUTHENTICATED);
      envelope.remove(Field.PACKET_AUTH_HEADER_MESSAGE);
      NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
    } catch (Exception ex) {
      logger.debug(
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus()),
          ex);
      markHandshakeAsFailed(envelope, session);
    }
  }

  private void markHandshakeAsFailed(final Envelope envelope, final NodeSession session) {
    envelope.remove(Field.PACKET_AUTH_HEADER_MESSAGE);
    session.cancelAllRequests("Failed to handshake");
  }
}
