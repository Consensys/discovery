/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.ethereum.beacon.discovery.schema.NodeSession.SessionState.AUTHENTICATED;

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
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.Functions;

/** Handles {@link HandshakeMessagePacket} in {@link Field#PACKET_HANDSHAKE} field */
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
    if (!HandlerUtil.requireField(Field.PACKET_HANDSHAKE, envelope)) {
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
                "Envelope %s in AuthHeaderMessagePacketHandler, requirements are satisfied!",
                envelope.getId()));

    HandshakeMessagePacket packet = envelope.get(Field.PACKET_HANDSHAKE);
    NodeSession session = envelope.get(Field.SESSION);
    try {

      if (session.getWhoAreYouChallenge().isEmpty()) {
        logger.debug(
            String.format("Outbound WhoAreYou challenge not found for session %s", session));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      Bytes whoAreYouChallenge = session.getWhoAreYouChallenge().get();

      Bytes ephemeralPubKeyCompressed = packet.getHeader().getAuthData().getEphemeralPubKey();
      Functions.HKDFKeys keys =
          Functions.hkdf_expand(
              session.getNodeId(),
              session.getHomeNodeId(),
              session.getStaticNodeKey(),
              ephemeralPubKeyCompressed,
              whoAreYouChallenge);
      // Swap keys because we are not initiator, other side is
      session.setInitiatorKey(keys.getRecipientKey());
      session.setRecipientKey(keys.getInitiatorKey());

      Optional<NodeRecord> enr = packet.getHeader().getAuthData().getNodeRecord(nodeRecordFactory);
      if (!enr.map(NodeRecord::isValid).orElse(true)) {
        logger.info(
            String.format(
                "Node record not valid for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getState()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      final Optional<NodeRecord> nodeRecordMaybe = session.getNodeRecord().or(() -> enr);
      // Check the node record matches the ID we expect
      if (!nodeRecordMaybe.map(r -> r.getNodeId().equals(session.getNodeId())).orElse(false)) {
        logger.info(
            String.format(
                "Incorrect node ID for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getState()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      NodeRecord nodeRecord = nodeRecordMaybe.get();

      boolean idNonceVerifyResult =
          packet
              .getHeader()
              .getAuthData()
              .verify(
                  whoAreYouChallenge,
                  session.getHomeNodeId(),
                  (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1));

      if (!idNonceVerifyResult) {
        logger.info(
            String.format(
                "ID signature not valid for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getState()));
        markHandshakeAsFailed(envelope, session);
        return;
      }

      Bytes16 maskingIV = envelope.get(Field.MASKING_IV);
      V5Message message =
          packet.decryptMessage(maskingIV, session.getRecipientKey(), nodeRecordFactory);
      envelope.put(Field.MESSAGE, message);

      enr.ifPresent(
          r -> {
            session.updateNodeRecord(r);
            session.getNodeTable().save(NodeRecordInfo.createDefault(r));
          });

      session.setState(AUTHENTICATED);
      envelope.remove(Field.PACKET_HANDSHAKE);
      NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
    } catch (Exception ex) {
      logger.debug(
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getState()),
          ex);
      markHandshakeAsFailed(envelope, session);
    }
  }

  private void markHandshakeAsFailed(final Envelope envelope, final NodeSession session) {
    envelope.remove(Field.PACKET_HANDSHAKE);
    session.cancelAllRequests("Failed to handshake");
  }
}
