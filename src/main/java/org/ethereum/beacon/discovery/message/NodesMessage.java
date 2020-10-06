/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.RlpDecodeException;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * NODES is the response to a FINDNODE or TOPICQUERY message. Multiple NODES messages may be sent as
 * responses to a single query.
 */
public class NodesMessage implements V5Message {
  // Unique request id
  private final Bytes requestId;
  // Total number of responses to the request
  private final Integer total;
  // List of nodes upon request
  private final List<NodeRecord> nodeRecords;

  public NodesMessage(Bytes requestId, Integer total,
      List<NodeRecord> nodeRecords) {
    this.requestId = requestId;
    this.total = total;
    this.nodeRecords = nodeRecords;
  }

  private static NodesMessage fromRlp(List<RlpType> rlpList, NodeRecordFactory nodeRecordFactory) {
    if (rlpList.size() != 3) {
      throw new RlpDecodeException("Invalid RLP list size for Nodes message-data: " + rlpList);
    }
    List<RlpType> nodeRecords = RlpUtil.asList(rlpList.get(2));
    return new NodesMessage(
        RlpUtil.asString(rlpList.get(0), RlpUtil.maxSize(MAX_REQUEST_ID_SIZE)),
        RlpUtil.asInteger(rlpList.get(1)),
            nodeRecords.stream()
                .map(rl -> nodeRecordFactory.fromRlpList(RlpUtil.asList(rl)))
                .collect(Collectors.toList()));
  }

  public static NodesMessage fromBytes(Bytes messageBytes, NodeRecordFactory nodeRecordFactory) {
    return fromRlp(RlpUtil.decodeSingleList(messageBytes), nodeRecordFactory);
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public int getTotal() {
    return total;
  }

  public synchronized List<NodeRecord> getNodeRecords() {
    return nodeRecords;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(getCode().byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArray()),
                    RlpString.create(total),
                    new RlpList(
                        getNodeRecords().stream()
                            .map(NodeRecord::asRlp)
                            .collect(Collectors.toList()))))));
  }

  @Override
  public MessageCode getCode() {
    return MessageCode.NODES;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodesMessage that = (NodesMessage) o;
    return requestId.equals(that.requestId) &&
        total.equals(that.total) &&
        nodeRecords.equals(that.nodeRecords);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestId, total, nodeRecords);
  }

  @Override
  public String toString() {
    return "NodesMessage{" +
        "requestId=" + requestId +
        ", total=" + total +
        ", nodeRecords=" + nodeRecords +
        '}';
  }
}
