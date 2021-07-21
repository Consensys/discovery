/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.util.Optional;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.task.TaskStatus;

public class NodesHandler implements MessageHandler<NodesMessage> {

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

    if (respHandler.handleResponseMessage(message, session)) {
      session.clearRequestInfo(message.getRequestId(), respHandler.getFoundNodes());
    } else {
      requestInfo.setTaskStatus(TaskStatus.IN_PROGRESS);
    }
  }
}
