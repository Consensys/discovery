/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.schema.NodeSession.SessionState;
import org.ethereum.beacon.discovery.task.TaskStatus;

/** Gets next request task in session and processes it */
public class NextTaskHandler implements EnvelopeHandler {
  private static final Logger LOG = LogManager.getLogger(NextTaskHandler.class);
  private static final int DEFAULT_DELAY_MS = 1000;
  private static final int RANDOM_MESSAGE_SIZE = 128;
  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;

  public NextTaskHandler(Pipeline outgoingPipeline, Scheduler scheduler) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
  }

  public static void tryToSendAwaitTaskIfAny(
      NodeSession session, Pipeline outgoingPipeline, Scheduler scheduler) {
    if (session.getFirstAwaitRequestInfo().isPresent()) {
      Envelope dummy = new Envelope();
      dummy.put(Field.SESSION, session);
      scheduler.executeWithDelay(
          Duration.ofMillis(DEFAULT_DELAY_MS), () -> outgoingPipeline.push(dummy));
    }
  }

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireSessionWithNodeRecord(envelope)) {
      return;
    }
    LOG.trace(
        () ->
            String.format(
                "Envelope %s in NextTaskHandler, requirements are satisfied!",
                envelope.getIdString()));

    NodeSession session = envelope.get(Field.SESSION);
    Optional<RequestInfo> requestInfoOpt = session.getFirstAwaitRequestInfo();
    if (requestInfoOpt.isEmpty()) {
      LOG.trace(() -> String.format("Envelope %s: no awaiting requests", envelope.getIdString()));
      return;
    }

    RequestInfo requestInfo = requestInfoOpt.get();
    LOG.trace(
        () ->
            String.format(
                "Envelope %s: processing awaiting request %s",
                envelope.getIdString(), requestInfo));

    if (session.getState().equals(SessionState.INITIAL)) {
      session.sendOutgoingRandom(Bytes.random(RANDOM_MESSAGE_SIZE, ThreadLocalRandom.current()));
      session.setState(SessionState.RANDOM_PACKET_SENT);
    } else if (session.getState().equals(SessionState.AUTHENTICATED)) {
      V5Message message = requestInfo.getMessage();
      session.sendOutgoingOrdinary(message);
      requestInfo.setTaskStatus(TaskStatus.SENT);
      tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
    }
  }
}
