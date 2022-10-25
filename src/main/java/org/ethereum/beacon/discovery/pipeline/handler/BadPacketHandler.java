/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;

/** Handles packet from {@link Field#BAD_PACKET}. Currently just logs it. */
public class BadPacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(BadPacketHandler.class);

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.BAD_PACKET, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in BadPacketHandler, requirements are satisfied!",
                envelope.getIdString()));

    logger.debug(
        () ->
            String.format(
                "Bad packet: %s in envelope #%s: %s",
                envelope.get(Field.BAD_PACKET),
                envelope.getIdString(),
                envelope.get(Field.BAD_EXCEPTION).toString()));
    // TODO: Reputation penalty etc
  }
}
