/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import com.google.common.base.Objects;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * PING checks whether the recipient is alive and informs it about the sender's ENR sequence number.
 */
public class PingMessage implements V5Message {
  // Unique request id
  private final Bytes requestId;
  // Local ENR sequence number of sender
  private final UInt64 enrSeq;

  public PingMessage(Bytes requestId, UInt64 enrSeq) {
    this.requestId = requestId;
    this.enrSeq = enrSeq;
  }

  public static PingMessage fromRlp(List<RlpType> rlpList) {
    return new PingMessage(
        Bytes.wrap(((RlpString) rlpList.get(0)).getBytes()),
        UInt64.fromBytes(Utils.leftPad(Bytes.wrap(((RlpString) rlpList.get(1)).getBytes()), 8)));
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public UInt64 getEnrSeq() {
    return enrSeq;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.PING.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArray()),
                    RlpString.create(enrSeq.toBigInteger())))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PingMessage that = (PingMessage) o;
    return Objects.equal(requestId, that.requestId) && Objects.equal(enrSeq, that.enrSeq);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, enrSeq);
  }

  @Override
  public String toString() {
    return "PingMessage{" + "requestId=" + requestId + ", enrSeq=" + enrSeq + '}';
  }
}
