package org.ethereum.beacon.discovery.packet5_1;

import org.ethereum.beacon.discovery.packet5_1.impl.OrdinaryMessageImpl.AuthDataImpl;
import org.ethereum.beacon.discovery.type.Bytes12;

public interface AuthData extends BytesSerializable {

  static AuthData create(Bytes12 gcmNonce) {
    return new AuthDataImpl(gcmNonce);
  }

  Bytes12 getAesGcmNonce();
}
