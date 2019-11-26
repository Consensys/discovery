/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.task.TaskType;

public class PongHandler implements MessageHandler<PongMessage> {
  @Override
  public void handle(PongMessage message, NodeSession session) {
    session.clearRequestId(message.getRequestId(), TaskType.PING);
  }
}
