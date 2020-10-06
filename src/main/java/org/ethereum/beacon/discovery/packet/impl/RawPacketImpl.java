/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.CryptoUtil;

public class RawPacketImpl extends AbstractBytes implements RawPacket {
  private static final int MASKING_IV_SIZE = 16;

  public static RawPacket create(Bytes16 iv, Packet<?> packet, Bytes16 destNodeId) {
    return new RawPacketImpl(Bytes.wrap(iv, mask(packet, iv, destNodeId)));
  }

  static Bytes mask(Packet<?> packet, Bytes16 maskingIV, Bytes16 destNodeId) {
    return Bytes.wrap(
        maskHeader(packet.getHeader(), maskingIV, destNodeId), packet.getMessageCyphered());
  }

  @VisibleForTesting
  public static Bytes maskHeader(Header<?> header, Bytes16 maskingIV, Bytes16 maskingKey) {
    Bytes headerPlainBytes =
        Bytes.concatenate(header.getStaticHeader().getBytes(), header.getAuthData().getBytes());
    return CryptoUtil.aesctrEncrypt(maskingKey, maskingIV, headerPlainBytes);
  }

  public static RawPacket create(Bytes data) {
    return new RawPacketImpl(data);
  }

  public RawPacketImpl(Bytes bytes) {
    super(checkMinSize(bytes, MASKING_IV_SIZE));
  }

  @Override
  public Bytes16 getMaskingIV() {
    return Bytes16.wrap(getBytes().slice(0, MASKING_IV_SIZE));
  }

  @Override
  public Packet<?> demaskPacket(Bytes16 destNodeId) {
    Packet<?> packet =
        PacketImpl.decrypt(getBytes().slice(MASKING_IV_SIZE), getMaskingIV(), destNodeId);
    packet.validate();
    return packet;
  }
}
