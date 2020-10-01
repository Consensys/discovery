/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.util.RlpUtil.CONS_UINT64;
import static org.ethereum.beacon.discovery.util.RlpUtil.enumSizes;
import static org.ethereum.beacon.discovery.util.RlpUtil.maxSize;
import static org.ethereum.beacon.discovery.util.RlpUtil.strictSize;

import com.google.common.base.Objects;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

/** PONG is the reply to PING {@link PingMessage} */
public class PongMessage implements V5Message {
  // Unique request id
  private final Bytes requestId;
  // Local ENR sequence number of sender
  private final UInt64 enrSeq;
  // 16 or 4 byte IP address of the intended recipient
  private final Bytes recipientIp;
  // recipient UDP port, a 16-bit integer
  private final int recipientPort;

  public PongMessage(Bytes requestId, UInt64 enrSeq, Bytes recipientIp, int recipientPort) {
    this.requestId = requestId;
    this.enrSeq = enrSeq;
    this.recipientIp = recipientIp;
    this.recipientPort = recipientPort;
  }

  public static PongMessage fromBytes(Bytes bytes) {
    List<Bytes> list =
        RlpUtil.decodeListOfStrings(
            bytes, maxSize(8), CONS_UINT64, enumSizes(4, 16), strictSize(2));
    return new PongMessage(
        list.get(0), UInt64.fromBytes(list.get(1)), list.get(2), list.get(3).toInt());
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public UInt64 getEnrSeq() {
    return enrSeq;
  }

  public Bytes getRecipientIp() {
    return recipientIp;
  }

  public int getRecipientPort() {
    return recipientPort;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.PONG.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArray()),
                    RlpString.create(enrSeq.toBigInteger()),
                    RlpString.create(recipientIp.toArray()),
                    RlpString.create(recipientPort)))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PongMessage that = (PongMessage) o;
    return Objects.equal(requestId, that.requestId)
        && Objects.equal(enrSeq, that.enrSeq)
        && Objects.equal(recipientIp, that.recipientIp)
        && Objects.equal(recipientPort, that.recipientPort);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, enrSeq, recipientIp, recipientPort);
  }

  @Override
  public String toString() {
    return "PongMessage{"
        + "requestId="
        + requestId
        + ", enrSeq="
        + enrSeq
        + ", recipientIp="
        + recipientIp
        + ", recipientPort="
        + recipientPort
        + '}';
  }
}
