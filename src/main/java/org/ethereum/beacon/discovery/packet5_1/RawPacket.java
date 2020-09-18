package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.impl.RawPacketImpl;
import org.ethereum.beacon.discovery.type.Bytes16;

public interface RawPacket extends BytesSerializable {

  static RawPacket create(Bytes16 iv, Packet<?> packet, Bytes16 homePeerId) {
    return RawPacketImpl.create(iv, packet, homePeerId);
  }

  static RawPacket decode(Bytes data) throws DecodeException {
    return RawPacketImpl.create(data);
  }

  Bytes16 getIV();

  Packet<?> decodePacket(Bytes16 homePeerId) throws DecodeException;
}
