/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.ethereum.beacon.discovery.schema.NodeSession.SessionState.AUTHENTICATED;

import java.util.Collection;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.pipeline.AbstractSkippingEnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.Functions;

/** Handles {@link HandshakeMessagePacket} in {@link Field#PACKET_HANDSHAKE} field */
public class HandshakeMessagePacketHandler extends AbstractSkippingEnvelopeHandler {
  private static final Logger LOG = LogManager.getLogger(HandshakeMessagePacketHandler.class);
  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;
  private final NodeRecordFactory nodeRecordFactory;
  private final NodeSessionManager nodeSessionManager;
  private final AddressAccessPolicy addressAccessPolicy;

  public HandshakeMessagePacketHandler(
      Pipeline outgoingPipeline,
      Scheduler scheduler,
      NodeRecordFactory nodeRecordFactory,
      NodeSessionManager nodeSessionManager,
      AddressAccessPolicy addressAccessPolicy) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
    this.nodeRecordFactory = nodeRecordFactory;
    this.nodeSessionManager = nodeSessionManager;
    this.addressAccessPolicy = addressAccessPolicy;
  }

  @Override
  protected void handlePacket(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.PACKET_HANDSHAKE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.MASKING_IV, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    LOG.trace(
        () ->
            String.format(
                "Envelope %s in HandshakeMessagePacketHandler, requirements are satisfied!",
                envelope.getIdString()));

    HandshakeMessagePacket packet = envelope.get(Field.PACKET_HANDSHAKE);
    NodeSession session = envelope.get(Field.SESSION);
    try {

      Collection<Bytes> pendingChallenges = session.getPendingWhoAreYouChallenges();
      if (pendingChallenges.isEmpty()) {
        LOG.trace(String.format("Outbound WhoAreYou challenge not found for session %s", session));
        markHandshakeAsFailed(envelope, session);
        return;
      }

      Optional<NodeRecord> enr = packet.getHeader().getAuthData().getNodeRecord(nodeRecordFactory);
      if (!enr.map(NodeRecord::isValid).orElse(true)) {
        LOG.trace(
            String.format(
                "Node record not valid for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getState()));
        markHandshakeAsFailed(envelope, session);
        return;
      }
      final Optional<NodeRecord> nodeRecordMaybe = session.getNodeRecord().or(() -> enr);
      // Check the node record matches the ID we expect
      if (!nodeRecordMaybe.map(r -> r.getNodeId().equals(session.getNodeId())).orElse(false)) {
        LOG.trace(
            "Incorrect node ID for message [{}] from node {} in status {}",
            packet,
            session.getNodeRecord(),
            session.getState());
        markHandshakeAsFailed(envelope, session);
        return;
      } else if (!enr.map(addressAccessPolicy::allow).orElse(true)) {
        LOG.trace(
            "Rejecting handshake from node {} because the ENR was disallowed: {}",
            session.getNodeRecord(),
            enr);
        markHandshakeAsFailed(envelope, session);
        return;
      }
      NodeRecord nodeRecord = nodeRecordMaybe.get();
      Bytes remotePubKey = (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);

      // The handshake packet does not directly identify which WHOAREYOU it answers, so we try
      // each pending challenge and accept the one whose ID signature verifies.
      Bytes ephemeralPubKeyCompressed = packet.getHeader().getAuthData().getEphemeralPubKey();
      Bytes matchedChallenge = null;
      for (Bytes candidate : pendingChallenges) {
        if (packet
            .getHeader()
            .getAuthData()
            .verify(candidate, session.getHomeNodeId(), remotePubKey)) {
          matchedChallenge = candidate;
          break;
        }
      }

      if (matchedChallenge == null) {
        LOG.trace(
            String.format(
                "ID signature not valid for message [%s] from node %s in status %s",
                packet, session.getNodeRecord(), session.getState()));
        markHandshakeAsFailed(envelope, session);
        return;
      }

      Functions.HKDFKeys keys =
          Functions.hkdfExpand(
              session.getNodeId(),
              session.getHomeNodeId(),
              session.getSigner(),
              ephemeralPubKeyCompressed,
              matchedChallenge);
      // Swap keys because we are not initiator, other side is
      session.setInitiatorKey(keys.getRecipientKey());
      session.setRecipientKey(keys.getInitiatorKey());

      Bytes16 maskingIV = envelope.get(Field.MASKING_IV);
      V5Message message =
          packet.decryptMessage(maskingIV, session.getRecipientKey(), nodeRecordFactory);
      envelope.put(Field.MESSAGE, message);

      session.setState(AUTHENTICATED);
      session.clearPendingWhoAreYouChallenges();
      envelope.remove(Field.PACKET_HANDSHAKE);
      enr.ifPresent(session::onNodeRecordReceived);
      NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
    } catch (Exception ex) {
      LOG.trace(
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getState()),
          ex);
      markHandshakeAsFailed(envelope, session);
    } catch (Throwable t) {
      LOG.debug(
          "Unexpected error while processing handshake [{}] from node {}",
          packet,
          session.getNodeRecord(),
          t);
      markHandshakeAsFailed(envelope, session);
    }
  }

  private void markHandshakeAsFailed(final Envelope envelope, final NodeSession session) {
    envelope.remove(Field.PACKET_HANDSHAKE);
    session.cancelAllRequests("Failed to handshake");
    nodeSessionManager.dropSession(session);
  }
}
