/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.processor;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.TalkHandler;
import org.ethereum.beacon.discovery.message.DiscoveryV5MessageDecoder;
import org.ethereum.beacon.discovery.message.MessageCode;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.message.handler.EnrUpdateTracker;
import org.ethereum.beacon.discovery.message.handler.ExternalAddressSelector;
import org.ethereum.beacon.discovery.message.handler.FindNodeHandler;
import org.ethereum.beacon.discovery.message.handler.MessageHandler;
import org.ethereum.beacon.discovery.message.handler.NodesHandler;
import org.ethereum.beacon.discovery.message.handler.PingHandler;
import org.ethereum.beacon.discovery.message.handler.PongHandler;
import org.ethereum.beacon.discovery.message.handler.EnrUpdateTracker.EnrUpdater;
import org.ethereum.beacon.discovery.message.handler.TalkReqHandler;
import org.ethereum.beacon.discovery.message.handler.TalkRespHandler;
import org.ethereum.beacon.discovery.schema.DiscoveryProtocol;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;

/**
 * {@link DiscoveryV5MessageDecoder} v5 messages processor. Uses several handlers, one for each type
 * of v5 message to handle appropriate message.
 */
public class DiscoveryV5MessageProcessor implements DiscoveryMessageProcessor<V5Message> {
  private static final Logger logger = LogManager.getLogger(DiscoveryV5MessageProcessor.class);

  @SuppressWarnings({"rawtypes"})
  private final Map<MessageCode, MessageHandler> messageHandlers = new HashMap<>();

  public DiscoveryV5MessageProcessor(
      LocalNodeRecordStore localNodeRecordStore, TalkHandler talkHandler, EnrUpdater enrUpdater) {
    final EnrUpdateTracker enrUpdateTracker = new EnrUpdateTracker(enrUpdater);
    messageHandlers.put(MessageCode.PING, new PingHandler(enrUpdateTracker));
    messageHandlers.put(
        MessageCode.PONG,
        new PongHandler(new ExternalAddressSelector(localNodeRecordStore), enrUpdateTracker));
    messageHandlers.put(MessageCode.FINDNODE, new FindNodeHandler());
    messageHandlers.put(MessageCode.NODES, new NodesHandler());
    messageHandlers.put(MessageCode.TALKREQ, new TalkReqHandler(talkHandler));
    messageHandlers.put(MessageCode.TALKRESP, new TalkRespHandler());
  }

  @Override
  public DiscoveryProtocol getSupportedIdentity() {
    return DiscoveryProtocol.V5;
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void handleMessage(V5Message message, NodeSession session) {
    MessageCode code = message.getCode();
    MessageHandler messageHandler = messageHandlers.get(code);
    logger.trace(() -> String.format("Handling message %s in session %s", message, session));
    if (messageHandler == null) {
      throw new RuntimeException("Not implemented yet");
    }
    messageHandler.handle(message, session);
  }
}
