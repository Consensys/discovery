/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.util.Functions;

public class FindNodeResponseHandler implements MultiPacketResponseHandler<NodesMessage> {
  private static final Logger logger = LogManager.getLogger(FindNodeResponseHandler.class);
  private static final int MAX_TOTAL_PACKETS = 16;
  private final List<NodeRecord> foundNodes = new ArrayList<>();
  private final Collection<Integer> distances;
  private int totalPackets = -1;
  private int receivedPackets = 0;

  public FindNodeResponseHandler(final Collection<Integer> distances) {
    this.distances = distances;
  }

  @Override
  public synchronized boolean handleResponseMessage(NodesMessage message, NodeSession session) {
    if (totalPackets == -1) {
      totalPackets = message.getTotal();
      if (totalPackets < 1 || totalPackets > MAX_TOTAL_PACKETS) {
        throw new RuntimeException("Invalid number of total packets: " + totalPackets);
      }
    } else {
      if (totalPackets != message.getTotal()) {
        throw new RuntimeException(
            "Total number differ in different packets for a single response: "
                + totalPackets
                + " != "
                + message.getTotal());
      }
    }
    receivedPackets++;

    // Parse node records
    logger.trace(
        () ->
            String.format(
                "Received %s node records in session %s. Total buckets expected: %s",
                message.getNodeRecords().size(), session, message.getTotal()));
    message.getNodeRecords().stream()
        .filter(this::isValid)
        .filter(record -> hasCorrectDistance(session, record))
        .forEach(
            nodeRecordV5 -> {
              foundNodes.add(nodeRecordV5);
              updateNodeRecord(session, nodeRecordV5);
            });

    return receivedPackets >= totalPackets;
  }

  public synchronized List<NodeRecord> getFoundNodes() {
    return foundNodes;
  }

  private void updateNodeRecord(final NodeSession session, final NodeRecord nodeRecordV5) {
    NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeRecordV5);
    final NodeTable nodeTable = session.getNodeTable();
    final Bytes nodeId = nodeRecordV5.getNodeId();
    final Optional<NodeRecordInfo> existingRecord = nodeTable.getNode(nodeId);
    if (isUpdateRequired(nodeRecordV5, existingRecord)) {
      // Update node table with new node record
      nodeTable.save(nodeRecordInfo);

      if (session.getNodeId().equals(nodeId)) {
        // Node sent us a new version of their own ENR, update the session.
        session.updateNodeRecord(nodeRecordV5);
      }
    }
  }

  private boolean isUpdateRequired(
      final NodeRecord newRecord, final Optional<NodeRecordInfo> existingRecord) {
    return existingRecord.isEmpty()
        || existingRecord.get().getNode().getSeq().compareTo(newRecord.getSeq()) < 0;
  }

  private boolean isValid(final NodeRecord record) {
    if (!record.isValid()) {
      logger.debug("Rejecting invalid node record {}", record);
      return false;
    }
    return true;
  }

  private boolean hasCorrectDistance(final NodeSession session, final NodeRecord nodeRecordV5) {
    final int actualDistance = Functions.logDistance(nodeRecordV5.getNodeId(), session.getNodeId());
    if (!distances.contains(actualDistance)) {
      logger.debug(
          "Rejecting node record {} received from {} because distance was not in {}.",
          nodeRecordV5.getNodeId(),
          session.getNodeId(),
          distances);
      return false;
    }
    return true;
  }
}
