/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Functions;

public class FindNodeResponseHandler implements MultiPacketResponseHandler<NodesMessage> {
  private static final Logger LOG = LogManager.getLogger(FindNodeResponseHandler.class);
  private static final int NOT_SET = -1;
  private static final int MAX_TOTAL_PACKETS = 16;
  private final List<NodeRecord> foundNodes = new ArrayList<>();
  private final Collection<Integer> distances;
  private final AddressAccessPolicy addressAccessPolicy;
  private int totalPackets = NOT_SET;
  private int receivedPackets = 0;

  public FindNodeResponseHandler(
      final Collection<Integer> distances, final AddressAccessPolicy addressAccessPolicy) {
    this.distances = distances;
    this.addressAccessPolicy = addressAccessPolicy;
  }

  @Override
  public synchronized boolean handleResponseMessage(NodesMessage message, NodeSession session) {
    if (totalPackets == NOT_SET) {
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
    LOG.trace(
        () ->
            String.format(
                "Received %s node records in session %s. Packet %s/%s.",
                message.getNodeRecords().size(), session, receivedPackets, message.getTotal()));
    message.getNodeRecords().stream()
        .filter(this::isValid)
        .filter(record -> hasCorrectDistance(session, record))
        .filter(addressAccessPolicy::allow)
        .forEach(
            nodeRecord -> {
              foundNodes.add(nodeRecord);
              session.onNodeRecordReceived(nodeRecord);
            });

    return receivedPackets >= totalPackets;
  }

  public synchronized List<NodeRecord> getFoundNodes() {
    return foundNodes;
  }

  private boolean isValid(final NodeRecord record) {
    if (!record.isValid()) {
      LOG.debug("Rejecting invalid node record {}", record);
      return false;
    }
    return true;
  }

  private boolean hasCorrectDistance(final NodeSession session, final NodeRecord nodeRecordV5) {
    final int actualDistance = Functions.logDistance(nodeRecordV5.getNodeId(), session.getNodeId());
    if (!distances.contains(actualDistance)) {
      LOG.debug(
          "Rejecting node record {} received from {} because distance was not in {}.",
          nodeRecordV5.getNodeId(),
          session.getNodeId(),
          distances);
      return false;
    }
    return true;
  }
}
