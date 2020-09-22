/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet5_0;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

/**
 * The WHOAREYOU packet, used during the handshake as a response to any message received from
 * unknown host
 *
 * <p>Format:<code>
 * whoareyou-packet = magic || [token, id-nonce, enr-seq]
 * magic = sha256(dest-node-id || "WHOAREYOU")
 * token = auth-tag of request
 * id-nonce = 32 random bytes
 * enr-seq = highest ENR sequence number of node A known on node B's side</code>
 */
@SuppressWarnings({"DefaultCharset"})
public class WhoAreYouPacket extends AbstractPacket {
  private static final Bytes MAGIC_BYTES = Bytes.wrap("WHOAREYOU".getBytes());
  private static final int MAGIC_FIELD_SIZE = 32;
  private static final int AUTH_TAG_SIZE = 12;
  private static final int ID_NONCE_SIZE = 32;

  private WhoAreYouDecoded decoded = null;

  public WhoAreYouPacket(Bytes bytes) {
    super(bytes);
  }

  /** Create a packet by converting {@code destNodeId} to a magic value */
  public static WhoAreYouPacket createFromNodeId(
      Bytes destNodeId, Bytes authTag, Bytes idNonce, UInt64 enrSeq) {
    Bytes magic = getStartMagic(destNodeId);
    return createFromMagic(magic, authTag, idNonce, enrSeq);
  }

  public static WhoAreYouPacket createFromMagic(
      Bytes magic, Bytes authTag, Bytes idNonce, UInt64 enrSeq) {
    byte[] rlpListEncoded =
        RlpEncoder.encode(
            new RlpList(
                RlpString.create(authTag.toArray()),
                RlpString.create(idNonce.toArray()),
                RlpString.create(enrSeq.toBigInteger())));
    return new WhoAreYouPacket(Bytes.concatenate(magic, Bytes.wrap(rlpListEncoded)));
  }

  /** Calculates first 32 bytes of WHOAREYOU packet */
  public static Bytes getStartMagic(Bytes destNodeId) {
    return Functions.hash(Bytes.concatenate(destNodeId, MAGIC_BYTES));
  }

  public Bytes getAuthTag() {
    decode();
    return decoded.authTag;
  }

  public Bytes getIdNonce() {
    decode();
    return decoded.idNonce;
  }

  public UInt64 getEnrSeq() {
    decode();
    return decoded.enrSeq;
  }

  public boolean isValid(Bytes destNodeId, Bytes expectedAuthTag) {
    decode();
    return Functions.hash(Bytes.concatenate(destNodeId, MAGIC_BYTES)).equals(decoded.magic)
        && expectedAuthTag.equals(getAuthTag());
  }

  private synchronized void decode() {
    if (decoded != null) {
      return;
    }
    WhoAreYouDecoded blank = new WhoAreYouDecoded();
    blank.magic = Bytes.wrap(getBytes().slice(0, MAGIC_FIELD_SIZE));
    List<Bytes> bytesList =
        RlpUtil.decodeListOfStrings(
            getBytes().slice(MAGIC_FIELD_SIZE), AUTH_TAG_SIZE, ID_NONCE_SIZE, RlpUtil.ANY_LEN);
    blank.authTag = bytesList.get(0);
    blank.idNonce = bytesList.get(1);
    blank.enrSeq = Utils.toUInt64(bytesList.get(2));
    this.decoded = blank;
  }

  @Override
  public String toString() {
    if (decoded != null) {
      return "WhoAreYou{"
          + "magic="
          + decoded.magic
          + ", authTag="
          + decoded.authTag
          + ", idNonce="
          + decoded.idNonce
          + ", enrSeq="
          + decoded.enrSeq
          + '}';
    } else {
      return "WhoAreYou{" + getBytes() + '}';
    }
  }

  private static class WhoAreYouDecoded {
    private Bytes magic; // Bytes32
    private Bytes authTag;
    private Bytes idNonce; // Bytes32
    private UInt64 enrSeq;
  }
}
