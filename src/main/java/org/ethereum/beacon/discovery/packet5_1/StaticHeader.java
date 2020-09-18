package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet5_1.impl.StaticHeaderImpl;

public interface StaticHeader extends BytesSerializable {

  String PROTOCOL_ID = "discv5  ";

  static StaticHeader create(Bytes32 sourcePeerId, Flag flag, int authDataSize) {
    return StaticHeaderImpl.create(PROTOCOL_ID, sourcePeerId, flag, authDataSize);
  }

  static StaticHeader decode(Bytes staticHeaderBytes) {
    return new StaticHeaderImpl(staticHeaderBytes);
  }

  String getProtocolId();

  Bytes32 getSourcePeerId();

  Flag getFlag();

  int getAuthDataSize();

  enum Flag {
    MESSAGE,
    WHOAREYOU,
    HANDSHAKE
  }

  default boolean isEqual(StaticHeader other) {
    return getProtocolId().equals(other.getProtocolId())
        && getSourcePeerId().equals(other.getSourcePeerId())
        && getFlag().equals(other.getFlag())
        && getAuthDataSize() == other.getAuthDataSize();
  }
}
