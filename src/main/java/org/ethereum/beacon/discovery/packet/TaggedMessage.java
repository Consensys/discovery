/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * Any message prepended with 'tag' and 'authTag' RLP
 *
 * <p>Format:<code>
 * tagged-message = tag || rlp_bytes(auth-tag) || message-payload
 * auth-tag = 12 random bytes unique to message
 * message-payload = any data</code>
 */
class TaggedMessage {
  public static final int TAG_SIZE = 32;
  public static final int RLP_AUTH_TAG_PREFIX_SIZE = 1;
  public static final int AUTH_TAG_SIZE = 12;
  public static final int RLP_AUTH_TAG_SIZE = AUTH_TAG_SIZE + RLP_AUTH_TAG_PREFIX_SIZE;
  public static final int HEADER_SIZE = TAG_SIZE + RLP_AUTH_TAG_SIZE;

  private final Bytes tag;
  private final Bytes authTag;
  private final Bytes payload;

  public static TaggedMessage decode(Bytes bytes) {
    Bytes tag = Bytes.wrap(bytes.slice(0, TAG_SIZE));
    Bytes authTagRlpBytes = bytes.slice(TAG_SIZE, RLP_AUTH_TAG_SIZE);
    Bytes authTag = decodeSingleRlpString(authTagRlpBytes);
    if (authTag.size() != AUTH_TAG_SIZE) {
      throw new RuntimeException("Invalid auth-tag size from RLP bytes: " + authTagRlpBytes);
    }
    Bytes payload = bytes.slice(TAG_SIZE + RLP_AUTH_TAG_SIZE);
    return new TaggedMessage(tag, authTag, payload);
  }

  private static Bytes decodeSingleRlpString(Bytes rlp) {
    RlpType item = decodeSingleRlpItem(rlp);
    if (!(item instanceof RlpString)) {
      throw new RuntimeException("Expected RLP bytes string, but got a list from bytes: " + rlp);
    }
    return Bytes.wrap(((RlpString) item).getBytes());
  }

  private static RlpType decodeSingleRlpItem(Bytes rlp) {
    List<RlpType> rlpList = RlpDecoder.decode(rlp.toArray()).getValues();
    if (rlpList.size() != 1) {
      throw new RuntimeException("Only a single RLP item expected from bytes: " + rlp);
    }
    return rlpList.get(0);
  }

  public TaggedMessage(Bytes tag, Bytes authTag, Bytes payload) {
    this.tag = tag;
    this.authTag = authTag;
    this.payload = payload;
  }

  public Bytes getTag() {
    return tag;
  }

  public Bytes getAuthTag() {
    return authTag;
  }

  public Bytes getPayload() {
    return payload;
  }

  public Bytes getBytes() {
    return Bytes.concatenate(
        tag, Bytes.wrap(RlpEncoder.encode(RlpString.create(authTag.toArray()))), payload);
  }

  @Override
  public String toString() {
    return "TaggedMessage{"
        + "tag="
        + tag
        + ", authTag="
        + authTag
        + ", payload size="
        + payload.size()
        + '}';
  }
}
