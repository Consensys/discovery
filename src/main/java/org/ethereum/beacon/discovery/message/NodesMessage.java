/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import com.google.common.base.Objects;
import java.util.List;
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
  private static final int TOTAL_PACKETS_BYTES_SIZE = 1;
  // Unique request id
  private final Bytes requestId;
  // Total number of responses to the request
  private final Integer total;
  // List of nodes upon request
  private final Supplier<List<NodeRecord>> nodeRecordsSupplier;
  // Size of nodes in current response
  private final Integer nodeRecordsSize;
  private List<NodeRecord> nodeRecords = null;

  public NodesMessage(Bytes requestId, Integer total, List<NodeRecord> nodeRecords) {
    this(requestId, total, () -> nodeRecords, nodeRecords.size());
  }

  public NodesMessage(
      Bytes requestId,
      Integer total,
      Supplier<List<NodeRecord>> nodeRecordsSupplier,
      Integer nodeRecordsSize) {
    this.requestId = requestId;
    this.total = total;
    this.nodeRecordsSupplier = nodeRecordsSupplier;
    this.nodeRecordsSize = nodeRecordsSize;
  }

  private static NodesMessage fromRlp(List<RlpType> rlpList, NodeRecordFactory nodeRecordFactory) {
    if (rlpList.size() != 3) {
      throw new RlpDecodeException("Invalid RLP list size for Nodes message-data: " + rlpList);
    }
    List<RlpType> nodeRecords = RlpUtil.asList(rlpList.get(2));
    return new NodesMessage(
        RlpUtil.asString(rlpList.get(0), RlpUtil.maxSize(MAX_REQUEST_ID_SIZE)),
        RlpUtil.asInteger(rlpList.get(1)),
        () ->
            nodeRecords.stream()
                .map(rl -> nodeRecordFactory.fromRlpList(RlpUtil.asList(rl)))
                .collect(Collectors.toList()),
        nodeRecords.size());
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
    if (nodeRecords == null) {
      this.nodeRecords = nodeRecordsSupplier.get();
    }
    return nodeRecords;
  }

  public int getNodeRecordsSize() {
    return nodeRecordsSize;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.NODES.byteCode()),
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NodesMessage that = (NodesMessage) o;
    return Objects.equal(requestId, that.requestId)
        && Objects.equal(total, that.total)
        && Objects.equal(nodeRecordsSize, that.nodeRecordsSize);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, total, nodeRecordsSize);
  }

  @Override
  public String toString() {
    return "NodesMessage{"
        + "requestId="
        + requestId
        + ", total="
        + total
        + ", nodeRecordsSize="
        + nodeRecordsSize
        + '}';
  }
}
