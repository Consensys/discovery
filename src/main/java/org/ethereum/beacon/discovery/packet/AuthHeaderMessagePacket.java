/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.ethereum.beacon.discovery.util.RlpUtil.DecodedList;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

/**
 * Used as first encrypted message sent in response to WHOAREYOU {@link WhoAreYouPacket}. Contains
 * an authentication header completing the handshake.
 *
 * <p>Format:<code>
 * message-packet = tag || auth-header || message
 * auth-header = [auth-tag, id-nonce, auth-scheme-name, ephemeral-pubkey, auth-response]
 * auth-scheme-name = "gcm"</code>
 *
 * <p>auth-response-pt is encrypted with a separate key, the auth-resp-key, using an all-zero nonce.
 * This is safe because only one message is ever encrypted with this key.
 *
 * <p><code>auth-response = aesgcm_encrypt(auth-resp-key, zero-nonce, auth-response-pt, "")
 * zero-nonce = 12 zero bytes
 * auth-response-pt = [version, id-nonce-sig, node-record]
 * version = 5
 * id-nonce-sig = sign(static-node-key, sha256("discovery-id-nonce" || id-nonce))
 * static-node-key = the private key used for node record identity
 * node-record = record of sender OR [] if enr-seq in WHOAREYOU != current seq
 * message = aesgcm_encrypt(initiator-key, auth-tag, message-pt, tag || auth-header)
 * message-pt = message-type || message-data
 * auth-tag = AES-GCM nonce, 12 random bytes unique to message</code>
 */
@SuppressWarnings({"DefaultCharset"})
public class AuthHeaderMessagePacket extends AbstractPacket {

  private static final Logger logger = LogManager.getLogger();
  public static final String AUTH_SCHEME_NAME = "gcm";
  public static final Bytes DISCOVERY_ID_NONCE = Bytes.wrap("discovery-id-nonce".getBytes());
  private static final Bytes ZERO_NONCE = Bytes.wrap(new byte[12]);
  public static final BigInteger AUTH_HEADER_VERSION = BigInteger.valueOf(5);
  private EphemeralPubKeyDecoded decodedEphemeralPubKeyPt = null;
  private MessagePtDecoded decodedMessagePt = null;

  public AuthHeaderMessagePacket(Bytes bytes) {
    super(bytes);
  }

  public static AuthHeaderMessagePacket create(
      Bytes tag, Bytes authHeader, Bytes messageCipherText) {
    return new AuthHeaderMessagePacket(Bytes.concatenate(tag, authHeader, messageCipherText));
  }

  public static Bytes createIdNonceMessage(Bytes idNonce, Bytes ephemeralPubkey) {
    Bytes message = Bytes.concatenate(DISCOVERY_ID_NONCE, idNonce, ephemeralPubkey);
    return message;
  }

  public static Bytes signIdNonce(Bytes idNonce, Bytes staticNodeKey, Bytes ephemeralPubkey) {
    Bytes signed =
        Functions.sign(
            staticNodeKey, Functions.hash(createIdNonceMessage(idNonce, ephemeralPubkey)));
    return signed;
  }

  public static byte[] createAuthMessagePt(Bytes idNonceSig, @Nullable NodeRecord nodeRecord) {
    return RlpEncoder.encode(
        new RlpList(
            RlpString.create(5),
            RlpString.create(idNonceSig.toArray()),
            nodeRecord == null ? new RlpList() : nodeRecord.asRlp()));
  }

  public static Bytes encodeAuthResponse(byte[] authResponsePt, Bytes authResponseKey) {
    return CryptoUtil.aesgcmEncrypt(
        authResponseKey, ZERO_NONCE, Bytes.wrap(authResponsePt), Bytes.EMPTY);
  }

  public static Bytes encodeAuthHeaderRlp(
      Bytes authTag, Bytes idNonce, Bytes ephemeralPubkey, Bytes authResponse) {
    RlpList authHeaderRlp =
        new RlpList(
            RlpString.create(authTag.toArray()),
            RlpString.create(idNonce.toArray()),
            RlpString.create(AUTH_SCHEME_NAME.getBytes()),
            RlpString.create(ephemeralPubkey.toArray()),
            RlpString.create(authResponse.toArray()));
    return Bytes.wrap(RlpEncoder.encode(authHeaderRlp));
  }

  public static AuthHeaderMessagePacket create(
      Bytes homeNodeId,
      Bytes destNodeId,
      Bytes authResponseKey,
      Bytes idNonce,
      Bytes staticNodeKey,
      @Nullable NodeRecord nodeRecord,
      Bytes ephemeralPubkey,
      Bytes authTag,
      Bytes initiatorKey,
      DiscoveryMessage message) {
    Bytes tag = Packet.createTag(homeNodeId, destNodeId);
    Bytes idNonceSig = signIdNonce(idNonce, staticNodeKey, ephemeralPubkey);
    byte[] authResponsePt = createAuthMessagePt(idNonceSig, nodeRecord);
    Bytes authResponse = encodeAuthResponse(authResponsePt, authResponseKey);
    Bytes authHeader = encodeAuthHeaderRlp(authTag, idNonce, ephemeralPubkey, authResponse);
    Bytes encryptedData = CryptoUtil.aesgcmEncrypt(initiatorKey, authTag, message.getBytes(), tag);
    return create(tag, authHeader, encryptedData);
  }

  public boolean isValid(Bytes expectedIdNonce, Bytes remoteNodePubKey) {
    verifyDecode();
    if (!AUTH_SCHEME_NAME.equals(decodedEphemeralPubKeyPt.authSchemeName)) {
      logger.trace("Incorrect auth scheme name: {}", decodedEphemeralPubKeyPt.authSchemeName);
      return false;
    }
    if (!expectedIdNonce.equals(getIdNonce())) {
      logger.trace("Incorrect IdNonce");
      return false;
    }
    if (!Functions.verifyECDSASignature(
        getIdNonceSig(),
        Functions.hash(createIdNonceMessage(getIdNonce(), getEphemeralPubkey())),
        remoteNodePubKey)) {
      logger.trace("Invalid signature");
      return false;
    }
    return true;
  }

  public Bytes getHomeNodeId(Bytes destNodeId) {
    verifyDecode();
    return Functions.hash(destNodeId).xor(decodedEphemeralPubKeyPt.tag);
  }

  public Bytes getAuthTag() {
    verifyDecode();
    return decodedEphemeralPubKeyPt.authTag;
  }

  public Bytes getIdNonce() {
    verifyDecode();
    return decodedEphemeralPubKeyPt.idNonce;
  }

  public Bytes getEphemeralPubkey() {
    verifyEphemeralPubKeyDecode();
    return decodedEphemeralPubKeyPt.ephemeralPubkey;
  }

  public Bytes getIdNonceSig() {
    verifyDecode();
    return decodedMessagePt.idNonceSig;
  }

  public NodeRecord getNodeRecord() {
    verifyDecode();
    return decodedMessagePt.nodeRecord;
  }

  public DiscoveryMessage getMessage() {
    verifyDecode();
    return decodedMessagePt.message;
  }

  private void verifyEphemeralPubKeyDecode() {
    if (decodedEphemeralPubKeyPt == null) {
      throw new RuntimeException("You should run decodeEphemeralPubKey before!");
    }
  }

  private void verifyDecode() {
    if (decodedEphemeralPubKeyPt == null || decodedMessagePt == null) {
      throw new RuntimeException("You should run decodeEphemeralPubKey and decodeMessage before!");
    }
  }

  public void decodeEphemeralPubKey() {
    if (decodedEphemeralPubKeyPt != null) {
      return;
    }
    EphemeralPubKeyDecoded blank = new EphemeralPubKeyDecoded();
    blank.tag = Bytes.wrap(getBytes().slice(0, 32));
    DecodedList decodeRes = RlpUtil.decodeFirstList(getBytes().slice(32));
    blank.messageEncrypted = decodeRes.getRemainingData();
    RlpList authHeaderParts = (RlpList) decodeRes.getList().getValues().get(0);
    // [auth-tag, id-nonce, auth-scheme-name, ephemeral-pubkey, auth-response]
    blank.authTag = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(0)).getBytes());
    blank.idNonce = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(1)).getBytes());
    blank.authSchemeName = new String(((RlpString) authHeaderParts.getValues().get(2)).getBytes());
    blank.ephemeralPubkey = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(3)).getBytes());
    blank.authResponse = Bytes.wrap(((RlpString) authHeaderParts.getValues().get(4)).getBytes());
    this.decodedEphemeralPubKeyPt = blank;
  }

  /** Run {@link AuthHeaderMessagePacket#decodeEphemeralPubKey()} before second part */
  public void decodeMessage(
      Bytes readKey, Bytes authResponseKey, NodeRecordFactory nodeRecordFactory) {
    if (decodedEphemeralPubKeyPt == null) {
      throw new RuntimeException("Run decodeEphemeralPubKey() before");
    }
    if (decodedMessagePt != null) {
      return;
    }
    MessagePtDecoded blank = new MessagePtDecoded();
    Bytes authResponsePt =
        CryptoUtil.aesgcmDecrypt(
            authResponseKey, ZERO_NONCE, decodedEphemeralPubKeyPt.authResponse, Bytes.EMPTY);
    RlpList authResponsePtParts = RlpUtil.decodeSingleList(authResponsePt);
    Preconditions.checkArgument(
        AUTH_HEADER_VERSION.equals(
            ((RlpString) authResponsePtParts.getValues().get(0)).asPositiveBigInteger()),
        "Invalid auth header version");

    blank.idNonceSig = Bytes.wrap(((RlpString) authResponsePtParts.getValues().get(1)).getBytes());
    RlpList nodeRecordDataList = ((RlpList) authResponsePtParts.getValues().get(2));
    blank.nodeRecord =
        nodeRecordDataList.getValues().isEmpty()
            ? null
            : nodeRecordFactory.fromRlpList(nodeRecordDataList);
    blank.message =
        new DiscoveryV5Message(
            CryptoUtil.aesgcmDecrypt(
                readKey,
                decodedEphemeralPubKeyPt.authTag,
                decodedEphemeralPubKeyPt.messageEncrypted,
                decodedEphemeralPubKeyPt.tag));
    this.decodedMessagePt = blank;
  }

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder("AuthHeaderMessagePacket{");
    if (decodedEphemeralPubKeyPt != null) {
      res.append("tag=")
          .append(decodedEphemeralPubKeyPt.tag)
          .append(", authTag=")
          .append(decodedEphemeralPubKeyPt.authTag)
          .append(", idNonce=")
          .append(decodedEphemeralPubKeyPt.idNonce)
          .append(", ephemeralPubkey=")
          .append(decodedEphemeralPubKeyPt.ephemeralPubkey);
    }
    if (decodedMessagePt != null) {
      res.append(", idNonceSig=")
          .append(decodedMessagePt.idNonceSig)
          .append(", nodeRecord=")
          .append(decodedMessagePt.nodeRecord)
          .append(", message=")
          .append(decodedMessagePt.message);
    }
    res.append('}');
    return res.toString();
  }

  private static class EphemeralPubKeyDecoded {
    private Bytes tag;
    private Bytes authTag;
    private Bytes idNonce;
    private String authSchemeName;
    private Bytes ephemeralPubkey;
    private Bytes authResponse;
    private Bytes messageEncrypted;
  }

  private static class MessagePtDecoded {
    private Bytes idNonceSig;
    private NodeRecord nodeRecord;
    private DiscoveryMessage message;
  }
}
