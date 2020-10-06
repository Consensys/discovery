/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeRequestInfo;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;
import org.ethereum.beacon.discovery.util.Functions;

public class NodesHandler implements MessageHandler<NodesMessage> {
  private static final Logger logger = LogManager.getLogger(NodesHandler.class);

  @Override
  public void handle(NodesMessage message, NodeSession session) {
    // NODES total count handling
    Optional<RequestInfo> requestInfoOpt = session.getRequestId(message.getRequestId());
    if (requestInfoOpt.isEmpty()) {
      throw new RuntimeException(
          String.format(
              "Request #%s not found in session %s when handling message %s",
              message.getRequestId(), session, message));
    }
    FindNodeRequestInfo requestInfo = (FindNodeRequestInfo) requestInfoOpt.get();
    int newNodesCount =
        requestInfo.getRemainingNodes() == null
            ? message.getTotal() - 1
            : requestInfo.getRemainingNodes() - 1;
    if (newNodesCount <= 0) {
      session.clearRequestId(message.getRequestId(), TaskType.FINDNODE);
    } else {
      session.updateRequestInfo(
          message.getRequestId(),
          new FindNodeRequestInfo(
              TaskStatus.IN_PROCESS,
              message.getRequestId(),
              requestInfo.getFuture(),
              requestInfo.getDistance(),
              newNodesCount));
    }

    // Parse node records
    logger.trace(
        () ->
            String.format(
                "Received %s node records in session %s. Total buckets expected: %s",
                message.getNodeRecords().size(), session, message.getTotal()));
    message.getNodeRecords().stream()
        .filter(this::isValid)
        .filter(record -> hasCorrectDistance(session, requestInfo, record))
        .forEach(
            nodeRecordV5 -> {
              NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeRecordV5);
              if (session.getNodeTable().getNode(nodeRecordV5.getNodeId()).isEmpty()) {
                session.getNodeTable().save(nodeRecordInfo);
              }
            });
  }

  private boolean isValid(final NodeRecord record) {
    if (!record.isValid()) {
      logger.debug("Rejecting invalid node record {}", record);
      return false;
    }
    return true;
  }

  private boolean hasCorrectDistance(
      final NodeSession session,
      final FindNodeRequestInfo requestInfo,
      final NodeRecord nodeRecordV5) {
    final int actualDistance = Functions.logDistance(nodeRecordV5.getNodeId(), session.getNodeId());
    final int requestedDistance = requestInfo.getDistance();
    if (actualDistance != requestedDistance) {
      logger.debug(
          "Rejecting node record {} received from {} because distance was not {}.",
          nodeRecordV5.getNodeId(),
          session.getNodeId(),
          requestInfo.getDistance());
      return false;
    }
    return true;
  }
}
