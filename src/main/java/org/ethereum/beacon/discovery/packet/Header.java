/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import static org.ethereum.beacon.discovery.packet.StaticHeader.PROTOCOL_ID;
import static org.ethereum.beacon.discovery.packet.StaticHeader.VERSION;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.packet.impl.HeaderImpl;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl.OrdinaryAuthDataImpl;
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

  static <TAuthData extends AuthData> Header<TAuthData> create(
      Flag flag, Bytes12 nonce, TAuthData authData) {

    StaticHeaderImpl staticHeader =
        StaticHeaderImpl.create(PROTOCOL_ID, VERSION, flag, nonce, authData.getBytes().size());
    return new HeaderImpl<>(staticHeader, authData);
  }

  static Header<OrdinaryAuthData> createOrdinaryHeader(Bytes32 srcNodeId, Bytes12 gcmNonce) {
    OrdinaryAuthData authData = new OrdinaryAuthDataImpl(srcNodeId);
    return create(Flag.MESSAGE, gcmNonce, authData);
  }

  static Header<WhoAreYouAuthData> createWhoAreYouHeader(
      Bytes12 requestNonce, Bytes16 idNonce, UInt64 enrSeq) {
    WhoAreYouAuthData authData = new WhoAreYouAuthDataImpl(idNonce, enrSeq);
    return create(Flag.WHOAREYOU, requestNonce, authData);
  }

  static Header<HandshakeAuthData> createHandshakeHeader(
      Bytes32 srcNodeId,
      Bytes12 nonce,
      Bytes idSignature,
      Bytes ephemeralPubKey,
      Optional<NodeRecord> nodeRecord) {
    HandshakeAuthData authData =
        HandshakeAuthDataImpl.create(srcNodeId, idSignature, ephemeralPubKey, nodeRecord);
    return create(Flag.HANDSHAKE, nonce, authData);
  }

  StaticHeader getStaticHeader();

  TAuthData getAuthData();

  int getSize();

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
