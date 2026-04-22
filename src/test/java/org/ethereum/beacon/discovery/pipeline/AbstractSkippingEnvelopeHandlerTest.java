/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AbstractSkippingEnvelopeHandlerTest {

  private static final Field<String> MARKER = new Field<>("MARKER");

  private static class CountingHandler extends AbstractSkippingEnvelopeHandler {
    final AtomicInteger invocations = new AtomicInteger();

    @Override
    protected void handlePacket(final Envelope envelope) {
      invocations.incrementAndGet();
    }
  }

  @Test
  void downstreamHandlersSkipEnvelopesMarkedAsBadPacket() {
    final EnvelopeHandler markBad =
        envelope -> {
          envelope.put(Field.BAD_PACKET, new Object());
          envelope.put(Field.BAD_EXCEPTION, new RuntimeException("bad"));
        };
    final CountingHandler downstream = new CountingHandler();
    final AtomicInteger terminalInvocations = new AtomicInteger();
    // Terminal handler implements EnvelopeHandler directly, so it still sees bad packets.
    final EnvelopeHandler terminal = envelope -> terminalInvocations.incrementAndGet();

    final Pipeline pipeline =
        new PipelineImpl().addHandler(markBad).addHandler(downstream).addHandler(terminal).build();

    pipeline.push(new Envelope());

    assertThat(downstream.invocations).hasValue(0);
    assertThat(terminalInvocations).hasValue(1);
  }

  @Test
  void healthyEnvelopesReachDownstreamSkippingHandlers() {
    final EnvelopeHandler markClean = envelope -> envelope.put(MARKER, "ok");
    final CountingHandler downstream =
        new CountingHandler() {
          @Override
          protected void handlePacket(final Envelope envelope) {
            assertThat(envelope.get(MARKER)).isEqualTo("ok");
            super.handlePacket(envelope);
          }
        };

    final Pipeline pipeline =
        new PipelineImpl().addHandler(markClean).addHandler(downstream).build();

    pipeline.push(new Envelope());

    assertThat(downstream.invocations).hasValue(1);
  }

  @Test
  void handlerChainStopsAtFirstBadMarking() {
    final CountingHandler first = new CountingHandler();
    final EnvelopeHandler markBadMidway = envelope -> envelope.put(Field.BAD_PACKET, new Object());
    final CountingHandler last = new CountingHandler();

    final Pipeline pipeline =
        new PipelineImpl().addHandler(first).addHandler(markBadMidway).addHandler(last).build();

    pipeline.push(new Envelope());

    assertThat(first.invocations).hasValue(1);
    assertThat(last.invocations).hasValue(0);
  }
}
