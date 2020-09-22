package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.impl.PacketImpl;
import org.ethereum.beacon.discovery.type.Bytes16;

public interface Packet<TAuthData extends AuthData> extends BytesSerializable {

  static Packet<?> decrypt(Bytes data, Bytes16 iv, Bytes16 nodeId) throws DecodeException {
    return PacketImpl.decrypt(data, iv, nodeId);
  }

  Bytes encrypt(Bytes16 iv, Bytes16 nodeId);

  Bytes getMessageBytes();

  Header<TAuthData> getHeader();
}
