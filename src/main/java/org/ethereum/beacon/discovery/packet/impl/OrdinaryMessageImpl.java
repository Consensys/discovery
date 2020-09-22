package org.ethereum.beacon.discovery.packet.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.type.Bytes12;

public class OrdinaryMessageImpl extends MessagePacketImpl<AuthData>
    implements OrdinaryMessagePacket {

  public OrdinaryMessageImpl(Header<AuthData> header, V5Message message, Bytes gcmKey) {
    this(header, encrypt(header, message, gcmKey));
  }

  public OrdinaryMessageImpl(Header<AuthData> header, Bytes cipheredMessage) {
    super(header, cipheredMessage);
  }

  public static class AuthDataImpl extends AbstractBytes implements AuthData {
    private static final int NONCE_OFF = 0;
    private static final int NONCE_SIZE = 12;
    private static final int AUTH_DATA_SIZE = NONCE_OFF + NONCE_SIZE;

    public AuthDataImpl(Bytes bytes) {
      super(checkStrictSize(bytes, AUTH_DATA_SIZE));
    }

    @Override
    public Bytes12 getAesGcmNonce() {
      return Bytes12.wrap(getBytes(), NONCE_OFF);
    }

    @Override
    public String toString() {
      return "AuthData{nonce=" + getAesGcmNonce() + "}";
    }
  }
}
