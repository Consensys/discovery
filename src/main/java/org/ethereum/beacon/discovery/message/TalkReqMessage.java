/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.util.RlpUtil.CONS_ANY;
import static org.ethereum.beacon.discovery.util.RlpUtil.maxSize;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

/**
 * TALKREQ sends an application-level request. The purpose of this message is pre-negotiating
 * connections made through another application-specific protocol identified by protocol.
 */
public class TalkReqMessage implements V5Message {

  // Unique request id
  private final Bytes requestId;

  private final Bytes protocol;

  private final Bytes request;

  public TalkReqMessage(Bytes requestId, Bytes protocol, Bytes request) {
    this.requestId = requestId;
    this.protocol = protocol;
    this.request = request;
  }

  public static TalkReqMessage fromBytes(Bytes bytes) {
    List<Bytes> list = RlpUtil.decodeListOfStrings(bytes, maxSize(8), CONS_ANY, CONS_ANY);
    return new TalkReqMessage(list.get(0), list.get(1), list.get(2));
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public Bytes getProtocol() {
    return protocol;
  }

  public Bytes getRequest() {
    return request;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.TALKREQ.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArrayUnsafe()),
                    RlpString.create(protocol.toArrayUnsafe()),
                    RlpString.create(request.toArrayUnsafe())))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TalkReqMessage that = (TalkReqMessage) o;
    return Objects.equals(requestId, that.requestId)
        && Objects.equals(protocol, that.protocol)
        && Objects.equals(request, that.request);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestId, protocol, request);
  }

  @Override
  public String toString() {
    return "TalkReqMessage{"
        + "requestId="
        + requestId
        + ", protocol="
        + protocol
        + ", request="
        + request
        + '}';
  }
}
