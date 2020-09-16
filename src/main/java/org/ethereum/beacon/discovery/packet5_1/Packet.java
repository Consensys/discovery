package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.type.Bytes12;

public interface Packet {

  Bytes getBytes();

  String getProtocolId();

  Bytes32 getSourcePeerId();

  Flag getFlag();

  Bytes getAuthData();

  Bytes12 getAesGcmNonce();

  enum Flag {
    MESSAGE,
    WHOAREYOU,
    HANDSHAKE
  }
}
