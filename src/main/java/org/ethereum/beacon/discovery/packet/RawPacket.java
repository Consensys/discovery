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
 * <p>{@code packet = masking-iv || masked-header || message }
 *
 * <p>The ciphered raw packet can extract just {@code masking-iv } field until decrypted
 */
public interface RawPacket extends BytesSerializable {

  static RawPacket create(Bytes16 maskingIV, Packet<?> packet, Bytes16 headerMaskingKey) {
    return RawPacketImpl.create(maskingIV, packet, headerMaskingKey);
  }

  static RawPacket decode(Bytes data) throws DecodeException {
    RawPacket rawPacket = RawPacketImpl.create(data);
    rawPacket.validate();
    return rawPacket;
  }

  Bytes16 getMaskingIV();

  Packet<?> decodePacket(Bytes16 headerMaskingKey) throws DecodeException;

  default Packet<?> decodePacket(Bytes homeNodeId) throws DecodeException {
    return decodePacket(Bytes16.wrap(homeNodeId, 0));
  }

  @Override
  default void validate() throws DecodeException {
    DecodeException.wrap(
        () -> "Couldn't decode IV: " + getBytes(),
        () -> {
          getMaskingIV();
        });
  }
}
