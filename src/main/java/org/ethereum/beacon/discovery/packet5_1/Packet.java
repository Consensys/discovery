package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.impl.PacketImpl;
import org.ethereum.beacon.discovery.type.Bytes16;

public interface Packet<TAuthData extends AuthData> extends BytesSerializable {

  static Packet<?> decrypt(Bytes data, Bytes16 iv, Bytes16 peerId) throws DecodeException {
    return PacketImpl.decrypt(data, iv, peerId);
  }

  Bytes encrypt(Bytes16 iv, Bytes16 peerId);

  Bytes getMessageBytes();

  Header<TAuthData> getHeader();
}
