/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import org.ethereum.beacon.discovery.message.TalkRespMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class TalkRespHandler implements MessageHandler<TalkRespMessage> {
  @Override
  public void handle(TalkRespMessage message, NodeSession session) {
    session.clearRequestInfo(message.getRequestId(), message.getResponse());
  }
}
