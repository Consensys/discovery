package org.ethereum.beacon.discovery.packet5_1.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet5_1.AuthData;
import org.ethereum.beacon.discovery.packet5_1.Header;
import org.ethereum.beacon.discovery.packet5_1.MessagePacket;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.CryptoUtil;

public abstract class MessagePacketImpl<TAuthData extends AuthData> extends PacketImpl<TAuthData>
    implements MessagePacket<TAuthData> {

  public static Bytes encrypt(Header<?> header, V5Message message, Bytes key) {
    return CryptoUtil.aesgcmEncrypt(
        key, header.getAuthData().getAesGcmNonce(), message.getBytes(), header.getBytes());
  }

  public MessagePacketImpl(Header<TAuthData> header, Bytes messageBytes) {
    super(header, messageBytes);
  }

  @Override
  public V5Message decryptMessage(Bytes key, NodeRecordFactory nodeRecordFactory) {
    DiscoveryV5Message decodedDiscoveryMessage =
        new DiscoveryV5Message(
            CryptoUtil.aesgcmDecrypt(
                key,
                getHeader().getAuthData().getAesGcmNonce(),
                getMessageBytes(),
                getHeader().getBytes()));
    return decodedDiscoveryMessage.create(nodeRecordFactory);
  }
}
