/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.DecodeException;

public abstract class MessagePacketImpl<TAuthData extends AuthData> extends PacketImpl<TAuthData>
    implements MessagePacket<TAuthData> {

  public static Bytes encrypt(Bytes16 maskingIV, Header<?> header, V5Message message, Bytes key) {
    return encrypt(
        Bytes.wrap(maskingIV, header.getBytes()), message.getBytes(),
        header.getStaticHeader().getNonce(), key);
  }

  public static Bytes encrypt(
      Bytes headerBytes, Bytes messageBytes, Bytes12 aesGcmNonce, Bytes key) {
    return CryptoUtil.aesgcmEncrypt(key, aesGcmNonce, messageBytes, headerBytes);
  }

  public MessagePacketImpl(Header<TAuthData> header, Bytes cipheredMessageBytes) {
    super(header, cipheredMessageBytes);
  }

  @Override
  public V5Message decryptMessage(Bytes16 maskingIV, Bytes key, NodeRecordFactory nodeRecordFactory) {
    Bytes messageAD = Bytes.wrap(maskingIV, getHeader().getBytes());
    DiscoveryV5Message decodedDiscoveryMessage =
        new DiscoveryV5Message(
            CryptoUtil.aesgcmDecrypt(
                key,
                getHeader().getStaticHeader().getNonce(),
                getMessageCyphered(),
                messageAD));
    try {
      return decodedDiscoveryMessage.create(nodeRecordFactory);
    } catch (Exception e) {
      throw new DecodeException("Error decoding message " + decodedDiscoveryMessage.getBytes(), e);
    }
  }
}
