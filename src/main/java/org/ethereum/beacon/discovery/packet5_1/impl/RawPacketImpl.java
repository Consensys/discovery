package org.ethereum.beacon.discovery.packet5_1.impl;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.Packet;
import org.ethereum.beacon.discovery.packet5_1.RawPacket;
import org.ethereum.beacon.discovery.type.Bytes16;

public class RawPacketImpl extends AbstractPacket implements RawPacket {
  private static final int IV_SIZE = 16;

  public static RawPacket create(Bytes iv, Packet packet, Bytes homePeerId) {
    // TODO
    return null;
  }

  public static RawPacket create(Bytes data) {
    return new RawPacketImpl(data);
  }

  public RawPacketImpl(Bytes bytes) {
    super(bytes, IV_SIZE);
  }

  @Override
  public Bytes16 getIV() {
    return Bytes16.wrap(getBytes().slice(0, IV_SIZE));
  }

  @Override
  public Packet decodePacket(Bytes16 homePeerId) {
    return PacketImpl.decode(getBytes().slice(IV_SIZE), getIV(), homePeerId);
  }
}
