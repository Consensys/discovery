/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.impl.PacketImpl;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;

public interface Packet<TAuthData extends AuthData> extends BytesSerializable {

  static Packet<?> decrypt(Bytes data, Bytes16 iv, Bytes16 nodeId) throws DecodeException {
    Packet<?> packet = PacketImpl.decrypt(data, iv, nodeId);
    packet.validate();
    return packet;
  }

  Bytes encrypt(Bytes16 iv, Bytes16 nodeId);

  Bytes getMessageBytes();

  Header<TAuthData> getHeader();

  @Override
  default void validate() throws DecodeException {
    getHeader().validate();
  }
}
