package org.ethereum.beacon.discovery.packet;

import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl.AuthDataImpl;
import org.ethereum.beacon.discovery.type.Bytes12;

public interface AuthData extends BytesSerializable {

  static AuthData create(Bytes12 gcmNonce) {
    return new AuthDataImpl(gcmNonce);
  }

  Bytes12 getAesGcmNonce();

  default boolean isEqual(AuthData other) {
    return getAesGcmNonce().equals(other.getAesGcmNonce());
  }
}