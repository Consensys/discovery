/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;

public abstract class PacketImpl<TAuthData extends AuthData> extends AbstractBytes
    implements Packet<TAuthData> {

  @SuppressWarnings("unchecked")
  public static Packet<?> decrypt(Bytes data, Bytes16 iv, Bytes16 destNodeId)
      throws DecodeException {
    Header<?> header = HeaderImpl.decrypt(data, iv, destNodeId);
    Bytes messageData = data.slice(header.getSize());
    switch (header.getStaticHeader().getFlag()) {
      case WHOAREYOU:
        if (messageData.size() > 0) {
          throw new DecodeException("Non-empty message data for WHOAREYOU packet");
        }
        return new WhoAreYouPacketImpl((Header<WhoAreYouAuthData>) header);
      case MESSAGE:
        return new OrdinaryMessageImpl((Header<OrdinaryAuthData>) header, messageData);
      case HANDSHAKE:
        return new HandshakeMessagePacketImpl((Header<HandshakeAuthData>) header, messageData);
      default:
        throw new DecodeException("Unknown flag: " + header.getStaticHeader().getFlag());
    }
  }

  private final Header<TAuthData> header;
  private final Bytes messageBytes;

  protected PacketImpl(Header<TAuthData> header, Bytes cipheredMessageBytes) {
    super(Bytes.wrap(header.getBytes(), cipheredMessageBytes));
    this.header = header;
    this.messageBytes = cipheredMessageBytes;
  }

  @Override
  public Header<TAuthData> getHeader() {
    return header;
  }

  @Override
  public Bytes getMessageCyphered() {
    return messageBytes;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "header="
        + header
        + ", cipherMsgSize="
        + getMessageCyphered().size()
        + '}';
  }
}
