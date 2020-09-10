/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet;

import com.google.common.base.Preconditions;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Sent if no session keys are available to initiate handshake
 *
 * <p>Format:<code>
 * random-packet = tag || rlp_bytes(auth-tag) || random-data
 * auth-tag = 12 random bytes unique to message
 * random-data = at least 44 bytes of random data</code>
 */
public class RandomPacket extends AbstractPacket {
  public static final int MIN_RANDOM_BYTES = 44;

  public static RandomPacket create(
      Bytes homeNodeId, Bytes destNodeId, Bytes authTag, Bytes randomBytes) {
    Bytes tag = Packet.createTag(homeNodeId, destNodeId);
    return create(tag, authTag, randomBytes);
  }

  public static RandomPacket create(Bytes tag, Bytes authTag, Bytes randomBytes) {
    // At least 44 bytes, spec defined
    Preconditions.checkArgument(
        randomBytes.size() >= MIN_RANDOM_BYTES,
        "Random bytes must be at least " + MIN_RANDOM_BYTES + " bytes");
    return new RandomPacket(new TaggedMessage(tag, authTag, randomBytes));
  }

  public static RandomPacket create(Bytes homeNodeId, Bytes destNodeId, Bytes authTag, Random rnd) {
    byte[] randomBytes = new byte[MIN_RANDOM_BYTES];
    rnd.nextBytes(randomBytes); // at least 44 bytes of random data, spec defined
    return create(homeNodeId, destNodeId, authTag, Bytes.wrap(randomBytes));
  }

  private TaggedMessage decoded = null;

  public RandomPacket(Bytes bytes) {
    super(bytes);
  }

  private RandomPacket(TaggedMessage decoded) {
    super(decoded.getBytes());
    this.decoded = decoded;
  }

  public Bytes getAuthTag() {
    decode();
    return decoded.getAuthTag();
  }

  public Bytes getTag() {
    decode();
    return decoded.getTag();
  }

  public Bytes getHomeNodeId(Bytes destNodeId) {
    return Functions.hash(destNodeId).xor(getTag());
  }

  private synchronized void decode() {
    if (decoded != null) {
      return;
    }
    if (getBytes().size() < TaggedMessage.HEADER_SIZE + MIN_RANDOM_BYTES) {
      throw new RuntimeException("RandomPacket is too small: " + getBytes().size());
    }
    this.decoded = TaggedMessage.decode(getBytes());
  }

  @Override
  public String toString() {
    if (decoded != null) {
      return "RandomPacket{"
          + "tag="
          + decoded.getTag()
          + ", authTag="
          + decoded.getAuthTag()
          + '}';
    } else {
      return "RandomPacket{" + getBytes() + '}';
    }
  }
}
