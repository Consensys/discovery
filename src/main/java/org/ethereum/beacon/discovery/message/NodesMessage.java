/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.util.RlpUtil.checkMaxSize;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.RlpUtil;

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

  public NodesMessage(Bytes requestId, Integer total, List<NodeRecord> nodeRecords) {
    this.requestId = requestId;
    this.total = total;
    this.nodeRecords = nodeRecords;
  }

  public static NodesMessage fromBytes(Bytes messageBytes, NodeRecordFactory nodeRecordFactory) {
    return RlpUtil.readRlpList(
        messageBytes,
        reader -> {
          final Bytes requestId = checkMaxSize(reader.readValue(), MAX_REQUEST_ID_SIZE);
          final int total = reader.readInt();
          final List<NodeRecord> nodeRecords = reader.readListContents(nodeRecordFactory::fromRlp);
          RlpUtil.checkComplete(reader);
          return new NodesMessage(requestId, total, nodeRecords);
        });
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
        RLP.encodeList(
            writer -> {
              writer.writeValue(requestId);
              writer.writeInt(total);
              writer.writeList(
                  getNodeRecords(),
                  (itemWriter, nodeRecord) -> {
                    nodeRecord.writeRlp(itemWriter);
                  });
            }));
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
    return requestId.equals(that.requestId)
        && total.equals(that.total)
        && nodeRecords.equals(that.nodeRecords);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestId, total, nodeRecords);
  }

  @Override
  public String toString() {
    return "NodesMessage{"
        + "requestId="
        + requestId
        + ", total="
        + total
        + ", nodeRecords="
        + nodeRecords
        + '}';
  }
}
