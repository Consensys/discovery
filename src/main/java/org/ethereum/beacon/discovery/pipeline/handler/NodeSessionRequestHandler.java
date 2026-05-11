/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.pipeline.AbstractSkippingEnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;

/**
 * Searches for node in {@link Field#NODE} and requests session resolving using {@link
 * Field#SESSION_LOOKUP}
 */
public class NodeSessionRequestHandler extends AbstractSkippingEnvelopeHandler {
  private static final Logger LOG = LogManager.getLogger(NodeSessionRequestHandler.class);

  @Override
  protected void handlePacket(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.NODE, envelope)) {
      return;
    }
    LOG.trace(
        () ->
            String.format(
                "Envelope %s in NodeSessionRequestHandler, requirements are satisfied!",
                envelope.getIdString()));

    envelope.put(Field.SESSION_LOOKUP, new SessionLookup(envelope.get(Field.NODE)));
  }
}
