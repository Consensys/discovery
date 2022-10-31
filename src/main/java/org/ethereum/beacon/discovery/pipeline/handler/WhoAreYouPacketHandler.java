/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.KeyPair;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.V5Message;
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
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.Functions;

/** Handles {@link WhoAreYouPacket} in {@link Field#PACKET_WHOAREYOU} field */
public class WhoAreYouPacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouPacketHandler.class);

  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;

  public WhoAreYouPacketHandler(final Pipeline outgoingPipeline, final Scheduler scheduler) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
  }

  @Override
  public void handle(final Envelope envelope) {
    if (!HandlerUtil.requireSessionWithNodeRecord(envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.PACKET_WHOAREYOU, envelope)) {
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
                "Envelope %s in WhoAreYouPacketHandler, requirements are satisfied!",
                envelope.getIdString()));

    WhoAreYouPacket whoAreYouPacket = envelope.get(Field.PACKET_WHOAREYOU);
    NodeSession session = envelope.get(Field.SESSION);
    try {
      final NodeRecord nodeRecord = session.getNodeRecord().orElseThrow();

      Bytes12 whoAreYouNonce = whoAreYouPacket.getHeader().getStaticHeader().getNonce();
      boolean nonceMatches =
          session.getLastOutboundNonce().map(whoAreYouNonce::equals).orElse(false);
      if (!nonceMatches) {
        logger.debug(
            "Verification not passed for message [{}] from node {} in status {}",
            whoAreYouPacket,
            nodeRecord,
            session.getState());
        envelope.remove(Field.PACKET_WHOAREYOU);
        session.cancelAllRequests("Bad WHOAREYOU received from node");
        return;
      }
      Bytes remotePubKey = (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);
      byte[] ephemeralKeyBytes = new byte[32];
      Functions.getRandom().nextBytes(ephemeralKeyBytes);
      KeyPair ephemeralKeyPair =
          Functions.createKeyPairFromSecretBytes(Bytes32.wrap(ephemeralKeyBytes));

      // The handshake uses the unmasked WHOAREYOU challenge as an input:
      // challenge-data     = masking-iv || static-header || authdata
      if (!envelope.contains(Field.MASKING_IV)) {
        throw new IllegalStateException("Internal error: No MASKING_IV field for WhoAreYou packet");
      }
      Bytes16 whoAreYouMaskingIV = envelope.get(Field.MASKING_IV);
      Bytes challengeData =
          Bytes.wrap(
              whoAreYouMaskingIV,
              whoAreYouPacket
                  .getHeader()
                  .getBytes() // this is effectively `static-header || authdata`
              );

      Bytes32 destNodeId = Bytes32.wrap(nodeRecord.getNodeId());
      Functions.HKDFKeys hkdfKeys =
          Functions.hkdf_expand(
              session.getHomeNodeId(),
              destNodeId,
              ephemeralKeyPair.secretKey(),
              remotePubKey,
              challengeData);
      session.setInitiatorKey(hkdfKeys.getInitiatorKey());
      session.setRecipientKey(hkdfKeys.getRecipientKey());
      final V5Message message =
          session
              .getFirstAwaitRequestInfo()
              .or(session::getFirstSentRequestInfo)
              .map(RequestInfo::getMessage)
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          String.format(
                              "Received WHOAREYOU in envelope #%s but no requests await in %s session",
                              envelope.getIdString(), session)));

      Bytes ephemeralPubKey =
          Functions.deriveCompressedPublicKeyFromPrivate(ephemeralKeyPair.secretKey());

      Bytes idSignature =
          HandshakeAuthData.signId(
              challengeData, ephemeralPubKey, destNodeId, session.getStaticNodeKey());

      NodeRecord respRecord = null;
      UInt64 lastKnownOurEnrVer = whoAreYouPacket.getHeader().getAuthData().getEnrSeq();

      if (lastKnownOurEnrVer.compareTo(session.getHomeNodeRecord().getSeq()) < 0
          || lastKnownOurEnrVer.isZero()) {
        respRecord = session.getHomeNodeRecord();
      }
      Header<HandshakeAuthData> header =
          Header.createHandshakeHeader(
              session.getHomeNodeId(),
              session.generateNonce(),
              idSignature,
              ephemeralPubKey,
              Optional.ofNullable(respRecord));
      session.setState(SessionState.AUTHENTICATED);

      session.sendOutgoingHandshake(header, message);

      envelope.remove(Field.PACKET_WHOAREYOU);
      NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
    } catch (Throwable ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              whoAreYouPacket, session.getNodeRecord(), session.getState());
      logger.debug(error, ex);
      envelope.remove(Field.PACKET_WHOAREYOU);
      session.cancelAllRequests("Bad WHOAREYOU received from node");
    }
  }
}
