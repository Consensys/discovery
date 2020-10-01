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
    List<Bytes> list = RlpUtil.decodeListOfStrings(bytes, maxSize(8), CONS_ANY);
    return new TalkRespMessage(list.get(0), list.get(1));
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
        Bytes.of(MessageCode.TALKRESP.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArray()), RlpString.create(response.toArray())))));
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
