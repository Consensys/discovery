package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.impl.RawPacketImpl;
import org.ethereum.beacon.discovery.type.Bytes16;

public interface RawPacket {

  static RawPacket create(Bytes iv, Packet packet, Bytes16 homePeerId) {
    // TODO
    return null;
  }

  static RawPacket create(Bytes data) throws PacketDecodeException {
    return RawPacketImpl.create(data);
  }

  Bytes getBytes();

  Bytes16 getIV();

  Packet decodePacket(Bytes16 homePeerId) throws PacketDecodeException;
}
