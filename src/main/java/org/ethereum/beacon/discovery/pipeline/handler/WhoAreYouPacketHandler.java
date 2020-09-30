/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.ethereum.beacon.discovery.util.Functions.PUBKEY_SIZE;

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.schema.NodeSession.SessionState;
import org.ethereum.beacon.discovery.task.TaskMessageFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.crypto.ECKeyPair;

/** Handles {@link WhoAreYouPacket} in {@link Field#PACKET_WHOAREYOU} field */
public class WhoAreYouPacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouPacketHandler.class);

  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;

  public WhoAreYouPacketHandler(Pipeline outgoingPipeline, Scheduler scheduler) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouPacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireNodeRecord(envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.PACKET_WHOAREYOU, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouPacketHandler, requirements are satisfied!",
                envelope.getId()));

    WhoAreYouPacket packet = (WhoAreYouPacket) envelope.get(Field.PACKET_WHOAREYOU);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    try {
      final NodeRecord nodeRecord = session.getNodeRecord().orElseThrow();

      if (!packet
          .getHeader()
          .getAuthData()
          .getRequestNonce()
          .equals(session.getAuthTag().orElseThrow())) {
        logger.error(
            "Verification not passed for message [{}] from node {} in status {}",
            packet,
            nodeRecord,
            session.getState());
        envelope.remove(Field.PACKET_WHOAREYOU);
        session.cancelAllRequests("Bad WHOAREYOU received from node");
        return;
      }
      Bytes remotePubKey = (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);
      byte[] ephemeralKeyBytes = new byte[32];
      Functions.getRandom().nextBytes(ephemeralKeyBytes);
      ECKeyPair ephemeralKey = ECKeyPair.create(ephemeralKeyBytes);

      Bytes32 idNonce = packet.getHeader().getAuthData().getIdNonce();
      Functions.HKDFKeys hkdfKeys =
          Functions.hkdf_expand(
              session.getHomeNodeId(),
              nodeRecord.getNodeId(),
              Bytes.wrap(ephemeralKeyBytes),
              remotePubKey,
              idNonce);
      session.setInitiatorKey(hkdfKeys.getInitiatorKey());
      session.setRecipientKey(hkdfKeys.getRecipientKey());
      Optional<RequestInfo> requestInfoOpt = session.getFirstAwaitRequestInfo();
      final V5Message message =
          requestInfoOpt
              .map(requestInfo -> TaskMessageFactory.createMessageFromRequest(requestInfo, session))
              .orElseThrow(
                  (Supplier<Throwable>)
                      () ->
                          new RuntimeException(
                              String.format(
                                  "Received WHOAREYOU in envelope #%s but no requests await in %s session",
                                  envelope.getId(), session)));

      Bytes ephemeralPubKey =
          Bytes.wrap(
              Utils.extractBytesFromUnsignedBigInt(ephemeralKey.getPublicKey(), PUBKEY_SIZE));

      Bytes idSignature =
          HandshakeAuthData.signId(idNonce, ephemeralPubKey, session.getStaticNodeKey());

      NodeRecord respRecord = null;
      if (packet
              .getHeader()
              .getAuthData()
              .getEnrSeq()
              .compareTo(session.getHomeNodeRecord().getSeq())
          < 0) {
        respRecord = session.getHomeNodeRecord();
      }
      Header<HandshakeAuthData> header =
          Header.createHandshakeHeader(
              session.getHomeNodeId(),
              session.generateNonce(),
              idSignature,
              ephemeralPubKey,
              Optional.ofNullable(respRecord));
      HandshakeMessagePacket handshakeMessagePacket =
          HandshakeMessagePacket.create(header, message, session.getInitiatorKey());
      session.setState(SessionState.AUTHENTICATED);

      session.sendOutgoing(handshakeMessagePacket);

      envelope.remove(Field.PACKET_WHOAREYOU);
      NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
    } catch (Throwable ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getState());
      logger.debug(error, ex);
      envelope.remove(Field.PACKET_WHOAREYOU);
      session.cancelAllRequests("Bad WHOAREYOU received from node");
    }
  }
}
