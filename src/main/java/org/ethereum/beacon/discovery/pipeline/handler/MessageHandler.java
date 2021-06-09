/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.TalkHandler;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.message.handler.PongHandler.EnrUpdater;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.processor.DiscoveryV5MessageProcessor;
import org.ethereum.beacon.discovery.processor.MessageProcessor;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;

public class MessageHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(MessageHandler.class);
  private final MessageProcessor messageProcessor;

  public MessageHandler(
      LocalNodeRecordStore localNodeRecordStore, TalkHandler talkHandler, EnrUpdater enrUpdater) {
    this.messageProcessor =
        new MessageProcessor(
            new DiscoveryV5MessageProcessor(localNodeRecordStore, talkHandler, enrUpdater));
  }

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.MESSAGE, envelope)) {
      return;
    }
    if (!HandlerUtil.requireNodeRecord(envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in MessageHandler, requirements are satisfied!", envelope.getId()));

    NodeSession session = envelope.get(Field.SESSION);
    V5Message message = envelope.get(Field.MESSAGE);
    try {
      messageProcessor.handleIncoming(message, session);
    } catch (Exception ex) {
      logger.trace(
          () ->
              String.format(
                  "Failed to handle message %s in envelope #%s", message, envelope.getId()),
          ex);
      envelope.put(Field.BAD_EXCEPTION, ex);
      envelope.remove(Field.MESSAGE);
    }
  }
}
