/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;

/**
 * For message packets, the authdata section is just the 96-bit AES/GCM nonce:
 *
 * <p>authdata = nonce authdata-size = 12
 */
public interface OrdinaryMessagePacket extends MessagePacket<OrdinaryAuthData> {

  static OrdinaryMessagePacket create(
      Bytes16 maskingIV, Header<OrdinaryAuthData> header, V5Message message, Bytes gcmKey) {
    return new OrdinaryMessageImpl(maskingIV, header, message, gcmKey);
  }

  static OrdinaryMessagePacket createRandom(Header<OrdinaryAuthData> header, Bytes randomData) {
    return new OrdinaryMessageImpl(header, randomData);
  }

  interface OrdinaryAuthData extends AuthData {

    Bytes32 getSourceNodeId();

    @Override
    default void validate() throws DecodeException {
      DecodeException.wrap(
          () -> "Couldn't decode Ordinary auth data: " + getBytes(),
          () -> {
            getSourceNodeId();
          });
    }
  }
}
