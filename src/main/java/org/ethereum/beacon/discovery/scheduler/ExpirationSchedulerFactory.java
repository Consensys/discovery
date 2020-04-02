package org.ethereum.beacon.discovery.scheduler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpirationSchedulerFactory {
  private final ScheduledExecutorService scheduler;

  public ExpirationSchedulerFactory(final ScheduledExecutorService scheduler) {
    this.scheduler = scheduler;
  }

  public <Key> ExpirationScheduler<Key> create(long delay, TimeUnit timeUnit) {
    return new ExpirationScheduler<>(delay, timeUnit, scheduler);
  }

  public void stop() {
    scheduler.shutdownNow();
  }
}
