package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.impl.HeaderImpl;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;

public interface Header<TAuthData extends AuthData> extends BytesSerializable {

  static Header<?> decrypt(Bytes headerBytes, Bytes16 iv, Bytes16 nodeId) {
    return HeaderImpl.decrypt(headerBytes, iv, nodeId);
  }

  static <TAuthData extends AuthData> Header<TAuthData> create(
      StaticHeader staticHeader, TAuthData authData) {
    return new HeaderImpl<>(staticHeader, authData);
  }

  static <TAuthData extends AuthData> Header<TAuthData> create(
      Bytes32 sourceNodeId, Flag flag, TAuthData authData) {
    return new HeaderImpl<>(
        StaticHeader.create(sourceNodeId, flag, authData.getBytes().size()), authData);
  }

  StaticHeader getStaticHeader();

  TAuthData getAuthData();

  int getSize();

  Bytes encrypt(Bytes16 iv, Bytes16 nodeId);

  default void validate() throws DecodeException {
    getStaticHeader().validate();
    getAuthData().validate();
    if (getStaticHeader().getAuthDataSize() != getAuthData().getBytes().size()) {
      throw new DecodeException(
          "Static header authdata-size field doesn't match the AuthData bytes size: "
              + getStaticHeader().getAuthDataSize()
              + " != "
              + getAuthData().getBytes().size());
    }
  }
}
