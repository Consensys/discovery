package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes52;

public interface WhoAreYouPacket extends Packet {

  default Bytes12 getRequestNonce() {
    return getAesGcmNonce();
  }

  Bytes32 getIdNonce();

  UInt64 getEnrSeq();

  @Override
  Bytes52 getAuthData();
}
