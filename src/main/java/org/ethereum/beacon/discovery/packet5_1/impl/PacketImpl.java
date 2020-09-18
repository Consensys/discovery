package org.ethereum.beacon.discovery.packet5_1.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.AuthData;
import org.ethereum.beacon.discovery.packet5_1.DecodeException;
import org.ethereum.beacon.discovery.packet5_1.HandshakeMessagePacket.HanshakeAuthData;
import org.ethereum.beacon.discovery.packet5_1.Header;
import org.ethereum.beacon.discovery.packet5_1.Packet;
import org.ethereum.beacon.discovery.packet5_1.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.type.Bytes16;

public abstract class PacketImpl<TAuthData extends AuthData> extends AbstractBytes
    implements Packet<TAuthData> {

  @SuppressWarnings("unchecked")
  public static Packet<?> decrypt(Bytes data, Bytes16 iv, Bytes16 peerId) throws DecodeException {
    Header<?> header = HeaderImpl.decrypt(data, iv, peerId);
    Bytes messageData = data.slice(header.getSize());
    switch (header.getStaticHeader().getFlag()) {
      case WHOAREYOU:
        if (messageData.size() > 0) {
          throw new DecodeException("Non-empty message data for WHOAREYOU packet");
        }
        return new WhoAreYouPacketImpl((Header<WhoAreYouAuthData>) header);
      case MESSAGE:
        return new OrdinaryMessageImpl((Header<AuthData>) header, messageData);
      case HANDSHAKE:
        return new HandshakeMessagePacketImpl((Header<HanshakeAuthData>) header, messageData);
      default:
        throw new DecodeException("Unknown flag: " + header.getStaticHeader().getFlag());
    }
  }

  private final Header<TAuthData> header;
  private final Bytes messageBytes;

  public PacketImpl(Header<TAuthData> header, Bytes messageBytes) {
    super(Bytes.wrap(header.getBytes(), messageBytes));
    this.header = header;
    this.messageBytes = messageBytes;
  }

  @Override
  public Bytes encrypt(Bytes16 iv, Bytes16 peerId) {
    return Bytes.wrap(header.encrypt(iv, peerId), getMessageBytes());
  }

  public Header<TAuthData> getHeader() {
    return header;
  }

  public Bytes getMessageBytes() {
    return messageBytes;
  }
}
