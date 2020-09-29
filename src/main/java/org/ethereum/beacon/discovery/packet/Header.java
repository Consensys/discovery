/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import static org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HANDSHAKE_VERSION;
import static org.ethereum.beacon.discovery.packet.StaticHeader.PROTOCOL_ID;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.packet.impl.HeaderImpl;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl.AuthDataImpl;
import org.ethereum.beacon.discovery.packet.impl.StaticHeaderImpl;
import org.ethereum.beacon.discovery.packet.impl.WhoAreYouPacketImpl.WhoAreYouAuthDataImpl;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;

/**
 * Header of any {@link Packet}
 *
 * <p>header = static-header || authdata
 */
public interface Header<TAuthData extends AuthData> extends BytesSerializable {

  static Header<?> decrypt(Bytes headerBytes, Bytes16 iv, Bytes16 nodeId) {
    return HeaderImpl.decrypt(headerBytes, iv, nodeId);
  }

  static <TAuthData extends AuthData> Header<TAuthData> create(
      Bytes32 sourceNodeId, Flag flag, TAuthData authData) {

    StaticHeaderImpl staticHeader = StaticHeaderImpl
        .create(PROTOCOL_ID, sourceNodeId, flag, authData.getBytes().size());
    return new HeaderImpl<>(staticHeader, authData);
  }

  static Header<AuthData> createOrdinaryHeader(Bytes32 srcNodeId, Bytes12 gcmNonce) {
    AuthData authData = new AuthDataImpl(gcmNonce);
    return create(srcNodeId, Flag.MESSAGE, authData);
  }

  static Header<WhoAreYouAuthData> createWhoAreYouHeader(
      Bytes32 srcNodeId, Bytes12 requestNonce, Bytes32 idNonce, UInt64 enrSeq) {
    WhoAreYouAuthData authData = new WhoAreYouAuthDataImpl(requestNonce, idNonce, enrSeq);
    return create(srcNodeId, Flag.WHOAREYOU, authData);
  }

  static Header<HandshakeAuthData> createHandshakeHeader(
      Bytes32 srcNodeId,
      Bytes12 nonce,
      Bytes idSignature,
      Bytes ephemeralPubKey,
      Optional<NodeRecord> nodeRecord) {
    HandshakeAuthData authData = HandshakeAuthDataImpl.create(
        HANDSHAKE_VERSION, nonce, idSignature, ephemeralPubKey, nodeRecord);
    return create(srcNodeId, Flag.HANDSHAKE, authData);
  }

  StaticHeader getStaticHeader();

  TAuthData getAuthData();

  int getSize();

  Bytes encrypt(Bytes16 iv, Bytes16 nodeId);

  @Override
  default void validate() throws DecodeException {
    getStaticHeader().validate();
    getAuthData().validate();
    if (getStaticHeader().getAuthDataSize() != getAuthData().getBytes().size()) {
      throw new DecodeException(
          "Static header authdata-size field doesn't match the AuthData bytes size: "
              + getStaticHeader().getAuthDataSize()
              + " != "
              + getAuthData().getBytes().size());
    }
  }
}
