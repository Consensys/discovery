/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.util.RlpUtil.checkMaxSize;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.ethereum.beacon.discovery.util.RlpUtil;

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
    return RlpUtil.readRlpList(
        bytes,
        reader -> {
          final Bytes requestId = checkMaxSize(reader.readValue(), MAX_REQUEST_ID_SIZE);
          final Bytes protocol = reader.readValue();
          final Bytes request = reader.readValue();
          RlpUtil.checkComplete(reader);
          return new TalkReqMessage(requestId, protocol, request);
        });
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
        Bytes.of(getCode().byteCode()),
        RLP.encodeList(
            writer -> {
              writer.writeValue(requestId);
              writer.writeValue(protocol);
              writer.writeValue(request);
            }));
  }

  @Override
  public MessageCode getCode() {
    return MessageCode.TALKREQ;
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
