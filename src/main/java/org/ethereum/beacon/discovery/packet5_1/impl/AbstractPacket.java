package org.ethereum.beacon.discovery.packet5_1.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.PacketDecodeException;

public abstract class AbstractPacket {

  private final Bytes bytes;

  public static void checkSize(Bytes bytes, int minimalSize) throws PacketDecodeException {
    if (bytes.size() < minimalSize) {
      throw new PacketDecodeException(
          "Packet is too small: " + bytes.size() + ", (expected at least " + minimalSize
              + " bytes)");
    }
  }

  public AbstractPacket(Bytes bytes, int minimalSize) {
    checkSize(bytes, minimalSize);
    this.bytes = bytes;
  }

  public Bytes getBytes() {
    return bytes;
  }
}
