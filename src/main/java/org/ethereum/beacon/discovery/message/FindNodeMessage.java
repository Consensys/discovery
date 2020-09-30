/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Objects;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * FINDNODE queries for nodes at the given logarithmic distance from the recipient's node ID. The
 * node IDs of all nodes in the response must have a shared prefix length of distance with the
 * recipient's node ID. A request with distance 0 should return the recipient's current record as
 * the only result.
 */
public class FindNodeMessage implements V5Message {
  // Unique request id
  private final Bytes requestId;
  // The requested log2 distance, a positive integer
  private final List<Integer> distances;

  public FindNodeMessage(Bytes requestId, List<Integer> distances) {
    checkArgument(!distances.isEmpty(), "Distances size should be > 0");
    this.requestId = requestId;
    this.distances = distances;
  }

  private static FindNodeMessage fromRlp(List<RlpType> rlpList) {
    Bytes requestId = Bytes.wrap(((RlpString) rlpList.get(0)).getBytes());
    RlpList distanceList = (RlpList) rlpList.get(1);
    List<Integer> distances =
        distanceList.getValues().stream()
            .map(rlpType -> (RlpString) rlpType)
            .map(s -> s.asPositiveBigInteger().intValueExact())
            .collect(Collectors.toList());

    return new FindNodeMessage(requestId, distances);
  }

  public static FindNodeMessage fromBytes(Bytes bytes) {
    return fromRlp(RlpUtil.decodeSingleList(bytes).getValues());
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public List<Integer> getDistances() {
    return distances;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.FINDNODE.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArray()),
                    new RlpList(
                        getDistances().stream()
                            .map(RlpString::create)
                            .collect(Collectors.toList()))))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FindNodeMessage that = (FindNodeMessage) o;
    return Objects.equal(requestId, that.requestId)
        && Objects.equal(getDistances(), that.getDistances());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, getDistances());
  }

  @Override
  public String toString() {
    return "FindNodeMessage{" + "requestId=" + requestId + ", distances=" + getDistances() + '}';
  }
}
