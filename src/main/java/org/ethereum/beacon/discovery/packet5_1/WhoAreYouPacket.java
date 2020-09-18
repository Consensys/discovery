package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet5_1.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.packet5_1.impl.WhoAreYouPacketImpl;
import org.ethereum.beacon.discovery.packet5_1.impl.WhoAreYouPacketImpl.WhoAreYouAuthDataImpl;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes52;

public interface WhoAreYouPacket extends Packet<WhoAreYouAuthData> {

  static WhoAreYouPacket create(Header<WhoAreYouAuthData> header) {
    return new WhoAreYouPacketImpl(header);
  }

  interface WhoAreYouAuthData extends AuthData {

    static WhoAreYouAuthData create(Bytes12 requestNonce, Bytes32 idNonce, UInt64 enrSeq) {
      return new WhoAreYouAuthDataImpl(requestNonce, idNonce, enrSeq);
    }

    default Bytes12 getRequestNonce() {
      return getAesGcmNonce();
    }

    Bytes32 getIdNonce();

    UInt64 getEnrSeq();

    @Override
    Bytes52 getBytes();

    default boolean isEqual(WhoAreYouAuthData other) {
      return getRequestNonce().equals(other.getRequestNonce())
          && getIdNonce().equals(other.getIdNonce())
          && getEnrSeq().equals(other.getEnrSeq());
    }
  }
}
