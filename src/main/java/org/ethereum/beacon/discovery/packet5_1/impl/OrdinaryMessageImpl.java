package org.ethereum.beacon.discovery.packet5_1.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet5_1.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet5_1.PacketDecodeException;
import org.ethereum.beacon.discovery.type.Bytes12;

public class OrdinaryMessageImpl extends PacketImpl implements OrdinaryMessagePacket {
  private static final int NONCE_OFF = 0;
  private static final int NONCE_SIZE = 12;
  private static final int AUTH_DATA_SIZE = NONCE_OFF + NONCE_SIZE;

  public OrdinaryMessageImpl(Header header, Bytes messageBytes) {
    super(header, messageBytes);
    if (header.getAuthDataBytes().size() != AUTH_DATA_SIZE) {
      throw new PacketDecodeException(
          "Invalid auth-data size for message packet: " + header.getAuthDataBytes().size());
    }
  }

  @Override
  public V5Message decryptMessage(Bytes key) {
    return null;
  }

  @Override
  public Bytes12 getAesGcmNonce() {
    return Bytes12.wrap(getAuthData(), NONCE_OFF);
  }

  @Override
  public Bytes12 getAuthData() {
    return Bytes12.wrap(super.getAuthData());
  }
}
