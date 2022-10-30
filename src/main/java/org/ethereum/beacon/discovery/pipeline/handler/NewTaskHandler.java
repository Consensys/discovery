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
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.schema.NodeSession;

/** Enqueues task in session for any task found in {@link Field#REQUEST} */
public class NewTaskHandler implements EnvelopeHandler {
  private static final Logger LOG = LogManager.getLogger(NewTaskHandler.class);

  @Override
  @SuppressWarnings("rawtypes")
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.REQUEST, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    LOG.trace(
        () ->
            String.format(
                "Envelope %s in NewTaskHandler, requirements are satisfied!",
                envelope.getIdString()));

    Request request = envelope.get(Field.REQUEST);
    NodeSession session = envelope.get(Field.SESSION);
    session.createNextRequest(request);
    envelope.remove(Field.REQUEST);
  }
}
