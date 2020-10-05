/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.processor;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.message.DiscoveryMessageDecoder;
import org.ethereum.beacon.discovery.message.Message;
import org.ethereum.beacon.discovery.schema.DiscoveryProtocol;
import org.ethereum.beacon.discovery.schema.NodeSession;

/**
 * Highest level processor which knows several processors for different versions of {@link
 * DiscoveryMessageDecoder}'s.
 */
public class MessageProcessor {
  private static final Logger logger = LogManager.getLogger(MessageProcessor.class);

  @SuppressWarnings({"rawtypes"})
  private final Map<DiscoveryProtocol, DiscoveryMessageProcessor> messageProcessors =
      new HashMap<>();

  @SuppressWarnings({"rawtypes"})
  public MessageProcessor(DiscoveryMessageProcessor... messageProcessors) {
    for (int i = 0; i < messageProcessors.length; ++i) {
      this.messageProcessors.put(messageProcessors[i].getSupportedIdentity(), messageProcessors[i]);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void handleIncoming(Message message, NodeSession session) {
    DiscoveryProtocol protocol = message.getDiscoveryProtocol();
    DiscoveryMessageProcessor messageHandler = messageProcessors.get(protocol);
    if (messageHandler == null) {
      String error =
          String.format(
              "Message %s with identity %s received in session %s is not supported",
              message, protocol, session);
      logger.debug(error);
      throw new RuntimeException(error);
    }
    messageHandler.handleMessage(message, session);
  }
}
