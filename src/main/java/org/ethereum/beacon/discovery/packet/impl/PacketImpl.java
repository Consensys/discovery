package org.ethereum.beacon.discovery.packet.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.DecodeException;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HanshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.type.Bytes16;

public abstract class PacketImpl<TAuthData extends AuthData> extends AbstractBytes
    implements Packet<TAuthData> {

  @SuppressWarnings("unchecked")
  public static Packet<?> decrypt(Bytes data, Bytes16 iv, Bytes16 nodeId) throws DecodeException {
    Header<?> header = HeaderImpl.decrypt(data, iv, nodeId);
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
  public Bytes encrypt(Bytes16 iv, Bytes16 nodeId) {
    return Bytes.wrap(header.encrypt(iv, nodeId), getMessageBytes());
  }

  public Header<TAuthData> getHeader() {
    return header;
  }

  public Bytes getMessageBytes() {
    return messageBytes;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "header="
        + header
        + ", cipherMsgSize="
        + getMessageBytes().size()
        + '}';
  }
}
