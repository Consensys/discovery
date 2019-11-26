/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet;

import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.Functions;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpString;

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
  private RandomPacketDecoded decoded = null;

  public RandomPacket(Bytes bytes) {
    super(bytes);
  }

  public static RandomPacket create(
      Bytes homeNodeId, Bytes destNodeId, Bytes authTag, Bytes randomBytes) {
    Bytes tag = Packet.createTag(homeNodeId, destNodeId);
    return create(tag, authTag, randomBytes);
  }

  public static RandomPacket create(Bytes tag, Bytes authTag, Bytes randomBytes) {
    assert randomBytes.size() >= MIN_RANDOM_BYTES; // At least 44 bytes, spec defined
    byte[] authTagRlp = RlpEncoder.encode(RlpString.create(authTag.toArray()));
    Bytes authTagEncoded = Bytes.wrap(authTagRlp);
    return new RandomPacket(Bytes.concatenate(tag, authTagEncoded, randomBytes));
  }

  public static RandomPacket create(Bytes homeNodeId, Bytes destNodeId, Bytes authTag, Random rnd) {
    byte[] randomBytes = new byte[MIN_RANDOM_BYTES];
    rnd.nextBytes(randomBytes); // at least 44 bytes of random data, spec defined
    return create(homeNodeId, destNodeId, authTag, Bytes.wrap(randomBytes));
  }

  public Bytes getHomeNodeId(Bytes destNodeId) {
    decode();
    return Functions.hash(destNodeId).xor(decoded.tag);
  }

  public Bytes getAuthTag() {
    decode();
    return decoded.authTag;
  }

  private synchronized void decode() {
    if (decoded != null) {
      return;
    }
    RandomPacketDecoded blank = new RandomPacketDecoded();
    blank.tag = Bytes.wrap(getBytes().slice(0, 32));
    blank.authTag =
        Bytes.wrap(
            ((RlpString)
                    RlpDecoder.decode(getBytes().slice(32, getBytes().size() - 32 - 44).toArray())
                        .getValues()
                        .get(0))
                .getBytes());
    this.decoded = blank;
  }

  @Override
  public String toString() {
    if (decoded != null) {
      return "RandomPacket{" + "tag=" + decoded.tag + ", authTag=" + decoded.authTag + '}';
    } else {
      return "RandomPacket{" + getBytes() + '}';
    }
  }

  private static class RandomPacketDecoded {
    private Bytes tag;
    private Bytes authTag;
  }
}
