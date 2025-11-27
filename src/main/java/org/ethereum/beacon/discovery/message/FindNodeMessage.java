/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static com.google.common.base.Preconditions.checkArgument;
import static org.ethereum.beacon.discovery.util.RlpUtil.checkMaxSize;

import com.google.common.base.Objects;
import java.util.List;
import org.apache.tuweni.v2.bytes.Bytes;
import org.apache.tuweni.v2.rlp.RLP;
import org.apache.tuweni.v2.rlp.RLPReader;
import org.apache.tuweni.v2.rlp.RLPWriter;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.RlpDecodeException;
import org.ethereum.beacon.discovery.util.RlpUtil;

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

  /**
   * According to the <a
   * href="https://github.com/ethereum/devp2p/blob/master/discv5/discv5-theory.md#node-table">Node
   * Discovery Protocol</a>, there are 256 buckets and the 0th bucket is reserved for our node.
   * Therefore, a distance must be greater than or equal to 0 and less than or equal to 256.
   *
   * @param distance The distance value to check.
   * @return True if the distance is valid.
   */
  public static boolean isValidDistance(final int distance) {
    return 0 <= distance && distance <= 256;
  }

  public static FindNodeMessage fromBytes(Bytes bytes) throws DecodeException {
    return RlpUtil.readRlpList(
        bytes,
        reader -> {
          final Bytes requestId = checkMaxSize(reader.readValue(), MAX_REQUEST_ID_SIZE);
          List<Integer> distances = reader.readListContents(RLPReader::readInt);
          for (Integer distance : distances) {
            if (!isValidDistance(distance)) {
              throw new RlpDecodeException("Invalid distance");
            }
          }
          return new FindNodeMessage(requestId, distances);
        });
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
    return Bytes.wrap(
        Bytes.of(getCode().byteCode()),
        RLP.encodeList(
            writer -> {
              writer.writeValue(requestId);
              writer.writeList(getDistances(), RLPWriter::writeInt);
            }));
  }

  @Override
  public MessageCode getCode() {
    return MessageCode.FINDNODE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
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
