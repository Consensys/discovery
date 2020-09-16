package org.ethereum.beacon.discovery.packet5_1.impl;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet5_1.Packet;
import org.ethereum.beacon.discovery.packet5_1.PacketDecodeException;
import org.ethereum.beacon.discovery.type.Bytes16;

public abstract class PacketImpl extends AbstractPacket implements Packet {

  public static Packet decode(Bytes data, Bytes16 iv, Bytes16 peerId) throws PacketDecodeException {
    Header header = Header.decode(data, iv, peerId);
    Bytes messageData = data.slice(header.getSize());
    switch (header.getStaticHeader().getFlag()) {
      case WHOAREYOU:
        if (messageData.size() > 0) {
          throw new PacketDecodeException("Non-empty message data for WHOAREYOU packet");
        }
        return new WhoAreYouPacketImpl(header);
      case MESSAGE:
        return new OrdinaryMessageImpl(header, messageData);
      case HANDSHAKE:
        return new HandshakeMessagePacketImpl(header, messageData);
      default:
        throw new PacketDecodeException("Unknown flag: " + header.getStaticHeader().getFlag());
    }
  }

  private final Header header;
  private final Bytes messageBytes;

  public PacketImpl(Header header, Bytes messageBytes) {
    super(Bytes.wrap(header.getBytes(), messageBytes), StaticHeader.STATIC_HEADER_SIZE);
    this.header = header;
    this.messageBytes = messageBytes;
  }

  public Header getHeader() {
    return header;
  }

  public Bytes getMessageBytes() {
    return messageBytes;
  }

  @Override
  public String getProtocolId() {
    return header.getStaticHeader().getProtocolId();
  }

  @Override
  public Bytes32 getSourcePeerId() {
    return header.getStaticHeader().getSourcePeerId();
  }

  @Override
  public Flag getFlag() {
    return header.getStaticHeader().getFlag();
  }

  @Override
  public Bytes getAuthData() {
    return header.getAuthDataBytes();
  }
}
