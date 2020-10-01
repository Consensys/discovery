/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.processor;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.TalkHandler;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.MessageCode;
import org.ethereum.beacon.discovery.message.handler.ExternalAddressSelector;
import org.ethereum.beacon.discovery.message.handler.FindNodeHandler;
import org.ethereum.beacon.discovery.message.handler.MessageHandler;
import org.ethereum.beacon.discovery.message.handler.NodesHandler;
import org.ethereum.beacon.discovery.message.handler.PingHandler;
import org.ethereum.beacon.discovery.message.handler.PongHandler;
import org.ethereum.beacon.discovery.message.handler.TalkReqHandler;
import org.ethereum.beacon.discovery.message.handler.TalkRespHandler;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.schema.Protocol;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;

/**
 * {@link DiscoveryV5Message} v5 messages processor. Uses several handlers, one for each type of v5
 * message to handle appropriate message.
 */
public class DiscoveryV5MessageProcessor implements DiscoveryMessageProcessor<DiscoveryV5Message> {
  private static final Logger logger = LogManager.getLogger(DiscoveryV5MessageProcessor.class);

  @SuppressWarnings({"rawtypes"})
  private final Map<MessageCode, MessageHandler> messageHandlers = new HashMap<>();

  private final NodeRecordFactory nodeRecordFactory;

  public DiscoveryV5MessageProcessor(
      NodeRecordFactory nodeRecordFactory,
      LocalNodeRecordStore localNodeRecordStore,
      TalkHandler talkHandler) {
    messageHandlers.put(MessageCode.PING, new PingHandler());
    messageHandlers.put(
        MessageCode.PONG, new PongHandler(new ExternalAddressSelector(localNodeRecordStore)));
    messageHandlers.put(MessageCode.FINDNODE, new FindNodeHandler());
    messageHandlers.put(MessageCode.NODES, new NodesHandler());
    messageHandlers.put(MessageCode.TALKREQ, new TalkReqHandler(talkHandler));
    messageHandlers.put(MessageCode.TALKRESP, new TalkRespHandler());
    this.nodeRecordFactory = nodeRecordFactory;
  }

  @Override
  public Protocol getSupportedIdentity() {
    return Protocol.V5;
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void handleMessage(DiscoveryV5Message message, NodeSession session) {
    MessageCode code = message.getCode();
    MessageHandler messageHandler = messageHandlers.get(code);
    logger.trace(() -> String.format("Handling message %s in session %s", message, session));
    if (messageHandler == null) {
      throw new RuntimeException("Not implemented yet");
    }
    messageHandler.handle(message.create(nodeRecordFactory), session);
  }
}
