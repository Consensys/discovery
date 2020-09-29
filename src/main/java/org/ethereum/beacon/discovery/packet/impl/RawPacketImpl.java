/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.type.Bytes16;

public class RawPacketImpl extends AbstractBytes implements RawPacket {
  private static final int IV_SIZE = 16;

  public static RawPacket create(Bytes16 iv, Packet<?> packet, Bytes16 destNodeId) {
    return new RawPacketImpl(Bytes.wrap(iv, packet.encrypt(iv, destNodeId)));
  }

  public static RawPacket create(Bytes data) {
    return new RawPacketImpl(data);
  }

  public RawPacketImpl(Bytes bytes) {
    super(checkMinSize(bytes, IV_SIZE));
  }

  @Override
  public Bytes16 getIV() {
    return Bytes16.wrap(getBytes().slice(0, IV_SIZE));
  }

  @Override
  public Packet<?> decodePacket(Bytes16 destNodeId) {
    Packet<?> packet = PacketImpl.decrypt(getBytes().slice(IV_SIZE), getIV(), destNodeId);
    packet.validate();
    return packet;
  }
}
