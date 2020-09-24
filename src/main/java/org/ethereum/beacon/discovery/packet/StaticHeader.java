package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet.impl.StaticHeaderImpl;
import org.ethereum.beacon.discovery.util.DecodeException;

public interface StaticHeader extends BytesSerializable {

  String PROTOCOL_ID = "discv5  ";

  static StaticHeader create(Bytes32 sourceNodeId, Flag flag, int authDataSize) {
    return StaticHeaderImpl.create(PROTOCOL_ID, sourceNodeId, flag, authDataSize);
  }

  static StaticHeader decode(Bytes staticHeaderBytes) {
    return new StaticHeaderImpl(staticHeaderBytes);
  }

  String getProtocolId();

  Bytes32 getSourceNodeId();

  Flag getFlag();

  int getAuthDataSize();

  default void validate() {
    if (!getProtocolId().equals(PROTOCOL_ID)) {
      throw new DecodeException("Invalid protocolId field: '" + getProtocolId() + "'");
    }
    DecodeException.wrap(() -> "Couldn't decode static header: " + getBytes(), () -> {
      getSourceNodeId();
      getFlag();
      getAuthDataSize();
    });
  }

  default boolean isEqual(StaticHeader other) {
    return getProtocolId().equals(other.getProtocolId())
        && getSourceNodeId().equals(other.getSourceNodeId())
        && getFlag().equals(other.getFlag())
        && getAuthDataSize() == other.getAuthDataSize();
  }

  enum Flag {
    MESSAGE,
    WHOAREYOU,
    HANDSHAKE
  }
}
