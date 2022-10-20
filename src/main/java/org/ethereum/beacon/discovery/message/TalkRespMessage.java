/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.util.RlpUtil.checkMaxSize;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.ethereum.beacon.discovery.util.RlpUtil;

/** TALKRESP is the response to TALKREQ. */
public class TalkRespMessage implements V5Message {

  // Unique request id
  private final Bytes requestId;

  private final Bytes response;

  public TalkRespMessage(Bytes requestId, Bytes response) {
    this.requestId = requestId;
    this.response = response;
  }

  public static TalkRespMessage fromBytes(Bytes bytes) {
    return RlpUtil.readRlpList(
        bytes,
        reader -> {
          final Bytes requestId = checkMaxSize(reader.readValue(), MAX_REQUEST_ID_SIZE);
          final Bytes response = reader.readValue();
          RlpUtil.checkComplete(reader);
          return new TalkRespMessage(requestId, response);
        });
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public Bytes getResponse() {
    return response;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(getCode().byteCode()),
        RLP.encodeList(
            writer -> {
              writer.writeValue(requestId);
              writer.writeValue(response);
            }));
  }

  @Override
  public MessageCode getCode() {
    return MessageCode.TALKRESP;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TalkRespMessage that = (TalkRespMessage) o;
    return Objects.equals(requestId, that.requestId) && Objects.equals(response, that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestId, response);
  }

  @Override
  public String toString() {
    return "TalkRespMessage{" + "requestId=" + requestId + ", request=" + response + '}';
  }
}
