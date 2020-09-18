package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet5_1.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet5_1.impl.HeaderImpl;
import org.ethereum.beacon.discovery.type.Bytes16;

public interface Header<TAuthData extends AuthData> extends BytesSerializable {

  static Header<?> decrypt(Bytes headerBytes, Bytes16 iv, Bytes16 peerId) {
    return HeaderImpl.decrypt(headerBytes, iv, peerId);
  }

  static <TAuthData extends AuthData> Header<TAuthData> create(
      StaticHeader staticHeader, TAuthData authData) {
    return new HeaderImpl<>(staticHeader, authData);
  }

  static <TAuthData extends AuthData> Header<TAuthData> create(
      Bytes32 sourcePeerId, Flag flag, TAuthData authData) {
    return new HeaderImpl<>(
        StaticHeader.create(sourcePeerId, flag, authData.getBytes().size()), authData);
  }

  StaticHeader getStaticHeader();

  TAuthData getAuthData();

  int getSize();

  Bytes encrypt(Bytes16 iv, Bytes16 peerId);
}
