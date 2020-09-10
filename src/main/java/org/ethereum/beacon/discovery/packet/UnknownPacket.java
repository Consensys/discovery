/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet;

import com.google.common.base.Preconditions;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;

/** Default packet form until its goal is known */
public class UnknownPacket extends AbstractPacket {
  private static final int MAX_SIZE = 1280;
  private static final int START_MAGIC_LENGTH = 32;

  public UnknownPacket(Bytes bytes) {
    super(bytes);
  }

  public MessagePacket getMessagePacket() {
    return new MessagePacket(getBytes());
  }

  public AuthHeaderMessagePacket getAuthHeaderMessagePacket() {
    return new AuthHeaderMessagePacket(getBytes());
  }

  public RandomPacket getRandomPacket() {
    return new RandomPacket(getBytes());
  }

  public TaggedMessage getTaggedMessage() {
    return TaggedMessage.decode(getBytes());
  }

  public WhoAreYouPacket getWhoAreYouPacket() {
    return new WhoAreYouPacket(getBytes());
  }

  public boolean isWhoAreYouPacket(Bytes destNodeId) {
    final Bytes bytes = getBytes();
    return bytes.size() >= START_MAGIC_LENGTH
        && WhoAreYouPacket.getStartMagic(destNodeId).equals(bytes.slice(0, START_MAGIC_LENGTH));
  }

  // tag              = xor(sha256(dest-node-id), src-node-id)
  // dest-node-id     = 32-byte node ID of B
  // src-node-id      = 32-byte node ID of A
  //
  // The recipient can recover the sender's ID by performing the same calculation in reverse.
  //
  // src-node-id      = xor(sha256(dest-node-id), tag)
  public Optional<Bytes> getSourceNodeId(Bytes destNodeId, Bytes destNodeIdHash) {
    Preconditions.checkArgument(!isWhoAreYouPacket(destNodeId));

    final Bytes bytes = getBytes();
    if (bytes.size() < START_MAGIC_LENGTH) {
      return Optional.empty();
    }
    Bytes xorTag = bytes.slice(0, START_MAGIC_LENGTH);
    return Optional.of(destNodeIdHash.xor(xorTag));
  }

  public void verify() {
    if (getBytes().size() > MAX_SIZE) {
      throw new RuntimeException(String.format("Packets should not exceed %s bytes", MAX_SIZE));
    }
  }

  @Override
  public String toString() {
    return "UnknownPacket{"
        + (getBytes().size() < 200
            ? getBytes()
            : getBytes().slice(0, 190) + "..." + "(" + getBytes().size() + " bytes)")
        + "}";
  }
}
