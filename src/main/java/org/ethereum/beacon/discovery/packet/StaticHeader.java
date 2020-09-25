/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet.impl.StaticHeaderImpl;
import org.ethereum.beacon.discovery.util.DecodeException;

/**
 * Static part of {@link Packet}'s {@link Header} static-header = protocol-id || src-id || flag ||
 * authdata-size
 */
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

  @Override
  default void validate() {
    if (!getProtocolId().equals(PROTOCOL_ID)) {
      throw new DecodeException("Invalid protocolId field: '" + getProtocolId() + "'");
    }
    DecodeException.wrap(
        () -> "Couldn't decode static header: " + getBytes(),
        () -> {
          getSourceNodeId();
          getFlag();
          getAuthDataSize();
        });
  }

  enum Flag {
    MESSAGE,
    WHOAREYOU,
    HANDSHAKE
  }
}
