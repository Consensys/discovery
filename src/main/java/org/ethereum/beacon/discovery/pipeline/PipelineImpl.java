/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

import static org.ethereum.beacon.discovery.pipeline.Field.INCOMING;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

public class PipelineImpl implements Pipeline {
  private static final Logger LOG = LogManager.getLogger();

  private final List<EnvelopeHandler> envelopeHandlers = new ArrayList<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private Flux<Envelope> pipeline = ReplayProcessor.cacheLast();
  private final FluxSink<Envelope> pipelineSink = ((ReplayProcessor<Envelope>) pipeline).sink();

  @Override
  public synchronized Pipeline build() {
    started.set(true);
    for (EnvelopeHandler handler : envelopeHandlers) {
      pipeline = pipeline.doOnNext(handler::handle);
    }
    Flux.from(pipeline)
        .onErrorContinue((err, msg) -> LOG.debug("Error while processing message: " + err))
        .subscribe();
    return this;
  }

  @Override
  public void push(Object object) {
    if (!started.get()) {
      throw new RuntimeException("You should build pipeline first");
    }
    if (!(object instanceof Envelope)) {
      Envelope envelope = new Envelope();
      envelope.put(INCOMING, object);
      pipelineSink.next(envelope);
    } else {
      pipelineSink.next((Envelope) object);
    }
  }

  @Override
  public Pipeline addHandler(EnvelopeHandler envelopeHandler) {
    if (started.get()) {
      throw new RuntimeException("Pipeline already started, couldn't add any handlers");
    }
    envelopeHandlers.add(envelopeHandler);
    return this;
  }

  @Override
  public Publisher<Envelope> getOutgoingEnvelopes() {
    return pipeline;
  }
}
