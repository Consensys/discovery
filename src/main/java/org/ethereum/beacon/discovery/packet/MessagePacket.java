/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Used when handshake is completed as a {@link DiscoveryMessage} authenticated container
 *
 * <p>Format:<code>
 * message-packet = tag || rlp_bytes(auth-tag) || message
 * message = aesgcm_encrypt(initiator-key, auth-tag, message-pt, tag)
 * message-pt = message-type || message-data</code>
 */
public class MessagePacket extends AbstractPacket {

  public static MessagePacket create(Bytes tag, Bytes authTag, Bytes messageCipherText) {
    return new MessagePacket(new TaggedMessage(tag, authTag, messageCipherText));
  }

  public static MessagePacket create(
      Bytes homeNodeId,
      Bytes destNodeId,
      Bytes authTag,
      Bytes initiatorKey,
      DiscoveryMessage message) {
    Bytes tag = Packet.createTag(homeNodeId, destNodeId);
    Bytes encryptedData = Functions.aesgcm_encrypt(initiatorKey, authTag, message.getBytes(), tag);
    return create(tag, authTag, encryptedData);
  }

  private TaggedMessage decodedTaggedMessage = null;
  private DiscoveryMessage decodedDiscoveryMessage = null;

  public MessagePacket(Bytes bytes) {
    super(bytes);
  }

  private MessagePacket(TaggedMessage taggedMessage) {
    super(taggedMessage.getBytes());
    this.decodedTaggedMessage = taggedMessage;
  }

  public Bytes getAuthTag() {
    return getTaggedMessage().getAuthTag();
  }

  public Bytes getHomeNodeId(Bytes destNodeId) {
    return Functions.hash(destNodeId)
        .xor(
            getTaggedMessage().getTag(),
            MutableBytes.create(getTaggedMessage().getTag().size()));
  }

  public DiscoveryMessage getMessage() {
    verifyDecode();
    return decodedDiscoveryMessage;
  }

  private void verifyDecode() {
    if (decodedDiscoveryMessage == null) {
      throw new RuntimeException("You should decode packet at first!");
    }
  }

  private TaggedMessage getTaggedMessage() {
    if (decodedTaggedMessage == null) {
      decodedTaggedMessage = TaggedMessage.decode(getBytes());
    }
    return decodedTaggedMessage;
  }

  public void decode(Bytes readKey) {
    if (decodedDiscoveryMessage != null) {
      return;
    }
    decodedDiscoveryMessage =
        new DiscoveryV5Message(
            Functions.aesgcm_decrypt(
                readKey,
                getTaggedMessage().getAuthTag(),
                getTaggedMessage().getPayload(),
                getTaggedMessage().getTag()));
  }

  @Override
  public String toString() {
    if (decodedDiscoveryMessage != null) {
      return "MessagePacket{"
          + "tag="
          + getTaggedMessage().getTag()
          + ", authTag="
          + getTaggedMessage().getAuthTag()
          + ", message="
          + getMessage()
          + '}';
    } else {
      return "MessagePacket{" + getBytes() + '}';
    }
  }
}
