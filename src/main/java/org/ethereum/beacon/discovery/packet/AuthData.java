/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl.AuthDataImpl;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.util.DecodeException;

/** AuthData part of any {@link Packet}'s {@link Header} */
public interface AuthData extends BytesSerializable {

  Bytes12 getAesGcmNonce();

  @Override
  default void validate() throws DecodeException {
    DecodeException.wrap(
        () -> "Couldn't decode AuthData nonce: " + getBytes(),
        () -> {
          getAesGcmNonce();
        });
  }
}
