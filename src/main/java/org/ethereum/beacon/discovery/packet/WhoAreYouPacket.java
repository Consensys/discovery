/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.packet.impl.WhoAreYouPacketImpl;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.type.Bytes52;
import org.ethereum.beacon.discovery.util.DecodeException;

/**
 * In WHOAREYOU packets, the authdata section contains information for the verification procedure.
 * The message field of WHOAREYOU packets is always empty.
 *
 * <p>authdata = request-nonce || id-nonce || enr-seq authdata-size = 52 request-nonce = uint96 --
 * nonce of request packet that couldn't be decrypted id-nonce = uint256 -- random bytes enr-seq =
 * uint64 -- ENR sequence number of the requesting node
 */
public interface WhoAreYouPacket extends Packet<WhoAreYouAuthData> {

  static WhoAreYouPacket create(Header<WhoAreYouAuthData> header) {
    return new WhoAreYouPacketImpl(header);
  }

  interface WhoAreYouAuthData extends AuthData {

    Bytes16 getIdNonce();

    UInt64 getEnrSeq();

    @Override
    default void validate() throws DecodeException {
      DecodeException.wrap(
          () -> "Couldn't decode WhoAreYou auth data: " + getBytes(),
          () -> {
            getIdNonce();
            getEnrSeq();
          });
    }
  }
}
