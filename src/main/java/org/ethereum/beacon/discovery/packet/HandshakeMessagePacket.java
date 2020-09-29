/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Handshake packet
 *
 * <p>For handshake message packets, the authdata section has variable size since public key and
 * signature sizes depend on the ENR identity scheme. For the "v4" identity scheme, we assume
 * 64-byte signature size and 33 bytes of (compressed) public key size.
 *
 * <p>authdata starts with a fixed-size authdata-head component, followed by the ID signature,
 * ephemeral public key and optional node record.
 *
 * <p>The record field may be omitted if the enr-seq of WHOAREYOU is recent enough, i.e. when it
 * matches the current sequence number of the sending node. If enr-seq is zero, the record must be
 * sent. Node records are encoded and verified as specified in EIP-778.
 *
 * <p>authdata = authdata-head || id-signature || eph-pubkey || record authdata-size = 15 + sig-size
 * + eph-key-size + len(record) authdata-head = version || nonce || sig-size || eph-key-size version
 * = uint8 -- value: 1 sig-size = uint8 -- value: 64 for ID scheme "v4" eph-key-size = uint8 --
 * value: 33 for ID scheme "v4"
 */
public interface HandshakeMessagePacket extends MessagePacket<HandshakeAuthData> {
  Bytes ID_SIGNATURE_PREFIX = Bytes.wrap("discovery-id-nonce".getBytes(StandardCharsets.US_ASCII));
  byte HANDSHAKE_VERSION = 1;

  static HandshakeMessagePacket create(
      Header<HandshakeAuthData> header, V5Message message, Bytes gcmKey) {
    return new HandshakeMessagePacketImpl(header, message, gcmKey);
  }

  interface HandshakeAuthData extends AuthData {

    static HandshakeAuthData create(
        Bytes12 nonce, Bytes idSignature, Bytes ephemeralPubKey, Optional<NodeRecord> nodeRecord) {
      return HandshakeAuthDataImpl.create(
          HANDSHAKE_VERSION, nonce, idSignature, ephemeralPubKey, nodeRecord);
    }

    static Header<HandshakeAuthData> createHeader(
        Bytes32 srcNodeId,
        Bytes12 nonce,
        Bytes idSignature,
        Bytes ephemeralPubKey,
        Optional<NodeRecord> nodeRecord) {
      HandshakeAuthData authData = create(nonce, idSignature, ephemeralPubKey, nodeRecord);
      return Header.create(srcNodeId, Flag.HANDSHAKE, authData);
    }

    /**
     * id-nonce-input = sha256("discovery-id-nonce" || id-nonce || ephemeral-pubkey) id-signature =
     * id_sign(id-nonce-input)
     */
    static Bytes signId(Bytes32 idNonce, Bytes ephemeralPubKey, Bytes homeNodePrivateKey) {
      Bytes idSignatureInput =
          CryptoUtil.sha256(Bytes.wrap(ID_SIGNATURE_PREFIX, idNonce, ephemeralPubKey));
      return Functions.sign(homeNodePrivateKey, idSignatureInput);
    }

    byte getVersion();

    Bytes getIdSignature();

    Bytes getEphemeralPubKey();

    Optional<NodeRecord> getNodeRecord(NodeRecordFactory nodeRecordFactory);

    @Override
    default void validate() throws DecodeException {
      AuthData.super.validate();
      if (getVersion() != HANDSHAKE_VERSION) {
        throw new DecodeException("Invalid Handshake version: " + getVersion());
      }
      DecodeException.wrap(
          () -> "Couldn't decode Handshake auth data: " + getBytes(),
          () -> {
            getIdSignature();
            getEphemeralPubKey();
          });
    }
  }
}
