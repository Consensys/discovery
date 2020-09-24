/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.impl.RawPacketImpl;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;

/**
 * Raw packet with encrypted (AES/CTR) header
 *
 * <p>{@code packet = iv || masked-header || message }
 *
 * <p>The ciphered raw packet can extract just {@code iv } field until decrypted
 */
public interface RawPacket extends BytesSerializable {

  static RawPacket create(Bytes16 iv, Packet<?> packet, Bytes16 headerMaskingKey) {
    return RawPacketImpl.create(iv, packet, headerMaskingKey);
  }

  static RawPacket decode(Bytes data) throws DecodeException {
    RawPacket rawPacket = RawPacketImpl.create(data);
    rawPacket.validate();
    return rawPacket;
  }

  Bytes16 getIV();

  Packet<?> decodePacket(Bytes16 headerMaskingKey) throws DecodeException;

  default Packet<?> decodePacket(Bytes homeNodeId) throws DecodeException {
    return decodePacket(Bytes16.wrap(homeNodeId, 0));
  }

  @Override
  default void validate() throws DecodeException {
    DecodeException.wrap(
        () -> "Couldn't decode IV: " + getBytes(),
        () -> {
          getIV();
        });
  }
}
