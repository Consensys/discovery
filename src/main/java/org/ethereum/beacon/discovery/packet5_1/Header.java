package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet5_1.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet5_1.impl.HeaderImpl;
import org.ethereum.beacon.discovery.type.Bytes16;

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
    if (!StaticHeader.PROTOCOL_ID.equals(getStaticHeader().getProtocolId())) {
      throw new DecodeException(
          "ProtocolId validation failed. Probably the header was incorrectly AES/CTR encrypted");
    }
  }
}
