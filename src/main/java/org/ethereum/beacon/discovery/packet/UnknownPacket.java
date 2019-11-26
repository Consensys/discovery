/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.type.Hashes;

/** Default packet form until its goal is known */
public class UnknownPacket extends AbstractPacket {
  private static final int MAX_SIZE = 1280;

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

  public WhoAreYouPacket getWhoAreYouPacket() {
    return new WhoAreYouPacket(getBytes());
  }

  public boolean isWhoAreYouPacket(Bytes destNodeId) {
    return WhoAreYouPacket.getStartMagic(destNodeId).equals(getBytes().slice(0, 32));
  }

  // tag              = xor(sha256(dest-node-id), src-node-id)
  // dest-node-id     = 32-byte node ID of B
  // src-node-id      = 32-byte node ID of A
  //
  // The recipient can recover the sender's ID by performing the same calculation in reverse.
  //
  // src-node-id      = xor(sha256(dest-node-id), tag)
  public Bytes getSourceNodeId(Bytes destNodeId) {
    assert !isWhoAreYouPacket(destNodeId);
    Bytes xorTag = getBytes().slice(0, 32);
    return Hashes.sha256(destNodeId).xor(xorTag);
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
