/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.type.Bytes52;

public class WhoAreYouPacketImpl extends PacketImpl<WhoAreYouAuthData> implements WhoAreYouPacket {

  public WhoAreYouPacketImpl(Header<WhoAreYouAuthData> header) {
    super(header, Bytes.EMPTY);
  }

  public static class WhoAreYouAuthDataImpl extends AbstractBytes implements WhoAreYouAuthData {
    private static final int ID_NONCE_OFF = 0;
    private static final int ID_NONCE_SIZE = 16;
    private static final int ENR_SEQ_OFF = ID_NONCE_OFF + ID_NONCE_SIZE;
    private static final int ENR_SEQ_SIZE = 8;
    private static final int AUTH_DATA_SIZE = ENR_SEQ_OFF + ENR_SEQ_SIZE;

    public WhoAreYouAuthDataImpl(Bytes16 idNonce, UInt64 enrSeq) {
      this(Bytes.concatenate(idNonce, enrSeq.toBytes()));
    }

    public WhoAreYouAuthDataImpl(Bytes bytes) {
      super(checkStrictSize(bytes, AUTH_DATA_SIZE));
    }

    @Override
    public Bytes16 getIdNonce() {
      return Bytes16.wrap(getBytes(), ID_NONCE_OFF);
    }

    @Override
    public UInt64 getEnrSeq() {
      return UInt64.fromBytes(getBytes().slice(ENR_SEQ_OFF, ENR_SEQ_SIZE));
    }

    @Override
    public String toString() {
      return "WhoAreYouAuthData{idNonce="
          + getIdNonce()
          + ", enrSeq="
          + getEnrSeq()
          + "}";
    }
  }
}
