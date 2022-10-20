/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.util.RlpUtil.checkMaxSize;
import static org.ethereum.beacon.discovery.util.RlpUtil.checkSizeEither;

import com.google.common.base.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.RlpUtil;

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
    return RlpUtil.readRlpList(
        bytes,
        reader -> {
          final Bytes requestId = checkMaxSize(reader.readValue(), MAX_REQUEST_ID_SIZE);
          final UInt64 enrSeq = UInt64.valueOf(reader.readBigInteger());
          final Bytes recipientIp = checkSizeEither(reader.readValue(), 4, 16);
          final int recipientPort = reader.readInt();
          return new PongMessage(requestId, enrSeq, recipientIp, recipientPort);
        });
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
        Bytes.of(getCode().byteCode()),
        RLP.encodeList(
            writer -> {
              writer.writeValue(requestId);
              writer.writeBigInteger(enrSeq.toBigInteger());
              writer.writeValue(recipientIp);
              writer.writeInt(recipientPort);
            }));
  }

  @Override
  public MessageCode getCode() {
    return MessageCode.PONG;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PongMessage that = (PongMessage) o;
    return Objects.equal(requestId, that.requestId)
        && Objects.equal(enrSeq, that.enrSeq)
        && Objects.equal(recipientIp, that.recipientIp)
        && recipientPort == that.recipientPort;
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
