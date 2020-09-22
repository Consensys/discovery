/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet5_0;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpString;

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
    checkArgument(bytes.size() >= HEADER_SIZE, "Too small message");
    Bytes tag = Bytes.wrap(bytes.slice(0, TAG_SIZE));
    Bytes authTagRlpBytes = bytes.slice(TAG_SIZE, RLP_AUTH_TAG_SIZE);
    Bytes authTag = RlpUtil.decodeSingleString(authTagRlpBytes, AUTH_TAG_SIZE);
    Bytes payload = bytes.slice(TAG_SIZE + RLP_AUTH_TAG_SIZE);
    return new TaggedMessage(tag, authTag, payload);
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
