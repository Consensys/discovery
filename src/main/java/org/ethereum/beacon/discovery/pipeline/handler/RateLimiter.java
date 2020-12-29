package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.scheduler.Scheduler;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter implements EnvelopeHandler {

  private static final Logger logger = LogManager.getLogger(MessageHandler.class);

  AtomicInteger numberOfMessagesProcessedThisSecond = new AtomicInteger(0);
  final Scheduler scheduler;

  final static int MAX_NUMBER_OF_MESSAGES_PER_SECOND = 500;

  public RateLimiter(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  public void start() {
    scheduler.executeAtFixedRate(
            Duration.ZERO,
            Duration.of(1, ChronoUnit.SECONDS),
            this::resetMessagesToZero
    ).exceptionally((err) -> {
      logger.warn("Error scheduling rate limiter reset. Trying again");
      this.start();
      return null;
    });
  }

  @Override
  public void handle(Envelope envelope) {
    final int numberOfMessages = numberOfMessagesProcessedThisSecond.incrementAndGet();
    if (numberOfMessages > MAX_NUMBER_OF_MESSAGES_PER_SECOND) {
      envelope.remove(Field.INCOMING);
    }
  }

  private void resetMessagesToZero() {
    numberOfMessagesProcessedThisSecond.set(0);
  }
}
