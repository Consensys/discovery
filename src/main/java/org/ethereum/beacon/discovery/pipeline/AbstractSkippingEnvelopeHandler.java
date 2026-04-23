/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

/**
 * Base class for {@link EnvelopeHandler}s that should be skipped once an envelope has been marked
 * with {@link Field#BAD_PACKET}. Subclasses implement {@link #handlePacket(Envelope)} and get the
 * skip behaviour for free; the terminal bad-packet handler implements {@link EnvelopeHandler}
 * directly.
 */
public abstract class AbstractSkippingEnvelopeHandler implements EnvelopeHandler {

  @Override
  public final void handle(final Envelope envelope) {
    if (envelope.contains(Field.BAD_PACKET)) {
      return;
    }
    handlePacket(envelope);
  }

  protected abstract void handlePacket(Envelope envelope);
}
