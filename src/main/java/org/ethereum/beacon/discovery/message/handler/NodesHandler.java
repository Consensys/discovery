/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.util.Functions;

public class NodesHandler implements MessageHandler<NodesMessage> {
  private static final Logger logger = LogManager.getLogger(NodesHandler.class);

  @Override
  public void handle(NodesMessage message, NodeSession session) {
    // NODES total count handling
    Optional<RequestInfo> requestInfoOpt = session.getRequestInfo(message.getRequestId());
    if (requestInfoOpt.isEmpty()) {
      throw new RuntimeException(
          String.format(
              "Request #%s not found in session %s when handling message %s",
              message.getRequestId(), session, message));
    }
    RequestInfo requestInfo = requestInfoOpt.get();
    FindNodeResponseHandler respHandler =
        (FindNodeResponseHandler) requestInfo.getRequest().getResponseHandler();

    if (respHandler.handleResponseMessage(message)) {
      session.clearRequestInfo(message.getRequestId(), null);
    } else {
      requestInfo.setTaskStatus(TaskStatus.IN_PROCESS);
    }
    // Parse node records
    logger.trace(
        () ->
            String.format(
                "Received %s node records in session %s. Total buckets expected: %s",
                message.getNodeRecords().size(), session, message.getTotal()));
    message.getNodeRecords().stream()
        .filter(this::isValid)
        .filter(
            record ->
                hasCorrectDistance(session, (FindNodeMessage) requestInfo.getMessage(), record))
        .forEach(nodeRecordV5 -> updateNodeRecord(session, nodeRecordV5));
  }

  private void updateNodeRecord(final NodeSession session, final NodeRecord nodeRecordV5) {
    NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeRecordV5);
    final NodeTable nodeTable = session.getNodeTable();
    final Bytes nodeId = nodeRecordV5.getNodeId();
    final Optional<NodeRecordInfo> existingRecord = nodeTable.getNode(nodeId);
    if (isUpdated(nodeRecordV5, existingRecord)) {
      // Update node table with new node record
      nodeTable.save(nodeRecordInfo);

      if (session.getNodeId().equals(nodeId)) {
        // Node sent us a new version of their own ENR, update the session.
        session.updateNodeRecord(nodeRecordV5);
      }
    }
  }

  private boolean isUpdated(
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

  private boolean hasCorrectDistance(
      final NodeSession session, final FindNodeMessage requestMsg, final NodeRecord nodeRecordV5) {
    final int actualDistance = Functions.logDistance(nodeRecordV5.getNodeId(), session.getNodeId());
    if (!requestMsg.getDistances().contains(actualDistance)) {
      logger.debug(
          "Rejecting node record {} received from {} because distance was not in {}.",
          nodeRecordV5.getNodeId(),
          session.getNodeId(),
          requestMsg.getDistances());
      return false;
    }
    return true;
  }
}
