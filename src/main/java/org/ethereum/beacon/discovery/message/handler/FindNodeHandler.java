/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class FindNodeHandler implements MessageHandler<FindNodeMessage> {
  private static final Logger LOG = LogManager.getLogger(FindNodeHandler.class);

  /**
   * The maximum size of any packet is 1280 bytes. Implementations should not generate or process
   * packets larger than this size. As per specification the maximum size of an ENR is 300 bytes. A
   * NODES message containing all FINDNODE response records would be at least 4800 bytes, not
   * including additional data such as the header. To stay below the size limit, NODES responses are
   * sent as multiple messages and specify the total number of responses in the message. 4х300 =
   * 1200 and we always have 80 bytes for everything else.
   */
  private static final int MAX_NODES_PER_MESSAGE = 4;

  /**
   * Implementations should limit the number of nodes in the result set. The recommended result
   * limit for FINDNODE queries is 16 nodes.
   */
  private static final int MAX_TOTAL_NODES_PER_RESPONSE = 16;

  public FindNodeHandler() {}

  @Override
  public void handle(FindNodeMessage message, NodeSession session) {
    List<NodeRecord> nodeRecordInfos =
        message.getDistances().stream()
            .distinct()
            .flatMap(session::getNodeRecordsInBucket)
            .limit(MAX_TOTAL_NODES_PER_RESPONSE)
            .collect(Collectors.toList());

    List<List<NodeRecord>> nodeRecordBatches =
        Lists.partition(nodeRecordInfos, MAX_NODES_PER_MESSAGE);

    LOG.trace(
        () ->
            String.format(
                "Sending %s nodes in reply to request with distances %s in session %s",
                nodeRecordInfos.size(), message.getDistances(), session));

    List<List<NodeRecord>> nonEmptyNodeRecordsList =
        nodeRecordBatches.isEmpty() ? singletonList(emptyList()) : nodeRecordBatches;

    nonEmptyNodeRecordsList.forEach(
        recordsList ->
            session.sendOutgoingOrdinary(
                new NodesMessage(
                    message.getRequestId(), nonEmptyNodeRecordsList.size(), recordsList)));
  }
}
