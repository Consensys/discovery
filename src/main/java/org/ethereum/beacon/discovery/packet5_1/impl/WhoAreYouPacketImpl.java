package org.ethereum.beacon.discovery.packet5_1.impl;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet5_1.PacketDecodeException;
import org.ethereum.beacon.discovery.packet5_1.WhoAreYouPacket;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes52;

public class WhoAreYouPacketImpl extends PacketImpl implements WhoAreYouPacket {
  private static final int REQ_NONCE_OFF = 0;
  private static final int REQ_NONCE_SIZE = 12;
  private static final int ID_NONCE_OFF = REQ_NONCE_OFF + REQ_NONCE_SIZE;
  private static final int ID_NONCE_SIZE = 32;
  private static final int ENR_SEQ_OFF = ID_NONCE_OFF + ID_NONCE_SIZE;
  private static final int ENR_SEQ_SIZE = 8;
  private static final int AUTH_DATA_SIZE = ENR_SEQ_OFF + ENR_SEQ_SIZE;

  public WhoAreYouPacketImpl(Header header) {
    super(header, Bytes.EMPTY);
    if (header.getAuthDataBytes().size() != AUTH_DATA_SIZE) {
      throw new PacketDecodeException(
          "Invalid auth-data size for WHOAREYOU packet: " + header.getAuthDataBytes().size());
    }
  }

  @Override
  public Bytes12 getAesGcmNonce() {
    return Bytes12.wrap(getAuthData(), REQ_NONCE_OFF);
  }

  @Override
  public Bytes32 getIdNonce() {
    return Bytes32.wrap(getAuthData(), ID_NONCE_OFF);
  }

  @Override
  public UInt64 getEnrSeq() {
    return UInt64.fromBytes(getAuthData().slice(ENR_SEQ_OFF, ENR_SEQ_SIZE));
  }

  @Override
  public Bytes52 getAuthData() {
    return Bytes52.wrap(super.getAuthData());
  }
}
