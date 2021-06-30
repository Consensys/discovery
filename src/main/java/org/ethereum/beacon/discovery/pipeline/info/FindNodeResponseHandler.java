/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import java.util.ArrayList;
import java.util.List;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class FindNodeResponseHandler implements MultiPacketResponseHandler<NodesMessage> {
  private static final int MAX_TOTAL_PACKETS = 16;
  private final List<NodeRecord> foundNodes = new ArrayList<>();
  private int totalPackets = -1;
  private int receivedPackets = 0;

  public synchronized boolean handleResponseMessage(
      NodesMessage msg, List<NodeRecord> validRecords) {
    final boolean requestCompleted = handleResponseMessage(msg);
    foundNodes.addAll(validRecords);
    return requestCompleted;
  }

  @Override
  public synchronized boolean handleResponseMessage(NodesMessage msg) {
    if (totalPackets == -1) {
      totalPackets = msg.getTotal();
      if (totalPackets < 1 || totalPackets > MAX_TOTAL_PACKETS) {
        throw new RuntimeException("Invalid number of total packets: " + totalPackets);
      }
    } else {
      if (totalPackets != msg.getTotal()) {
        throw new RuntimeException(
            "Total number differ in different packets for a single response: "
                + totalPackets
                + " != "
                + msg.getTotal());
      }
    }
    receivedPackets++;
    return receivedPackets >= totalPackets;
  }

  public synchronized List<NodeRecord> getFoundNodes() {
    return foundNodes;
  }
}
