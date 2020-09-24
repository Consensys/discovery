/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HanshakeAuthData;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.Functions;

public interface HandshakeMessagePacket extends MessagePacket<HanshakeAuthData> {
  Bytes ID_SIGNATURE_PREFIX = Bytes.wrap("discovery-id-nonce".getBytes(StandardCharsets.US_ASCII));
  byte HANDSHAKE_VERSION = 1;

  static HandshakeMessagePacket create(
      Header<HanshakeAuthData> header, V5Message message, Bytes gcmKey) {
    return new HandshakeMessagePacketImpl(header, message, gcmKey);
  }

  interface HanshakeAuthData extends AuthData {

    static HanshakeAuthData create(
        Bytes12 nonce, Bytes idSignature, Bytes ephemeralPubKey, Optional<NodeRecord> nodeRecord) {
      return HandshakeAuthDataImpl.create(
          HANDSHAKE_VERSION, nonce, idSignature, ephemeralPubKey, nodeRecord);
    }

    static Header<HanshakeAuthData> createHeader(
        Bytes32 srcNodeId,
        Bytes12 nonce,
        Bytes idSignature,
        Bytes ephemeralPubKey,
        Optional<NodeRecord> nodeRecord) {
      HanshakeAuthData authData = create(nonce, idSignature, ephemeralPubKey, nodeRecord);
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

    default boolean isEqualTo(HanshakeAuthData other, NodeRecordFactory nodeRecordFactory) {
      return getVersion() == other.getVersion()
          && getIdSignature().equals(other.getIdSignature())
          && getEphemeralPubKey().equals(other.getEphemeralPubKey())
          && getNodeRecord(nodeRecordFactory).equals(other.getNodeRecord(nodeRecordFactory));
    }
  }
}
