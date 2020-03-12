/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import com.google.common.collect.Sets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/**
 * Sends {@link TaskType#PING} to closest NodeRecords added via {@link #add(NodeRecordInfo,
 * Runnable, Runnable)}. Tasks is called failed if timeout is reached and reply from node is not
 * received.
 */
public class LiveCheckTasks {
  private static final Logger logger = LogManager.getLogger();
  private final Scheduler scheduler;
  private final DiscoveryManager discoveryManager;
  private final Set<Bytes> currentTasks = Sets.newConcurrentHashSet();
  private final ExpirationScheduler<Bytes> taskTimeouts;

  public LiveCheckTasks(DiscoveryManager discoveryManager, Scheduler scheduler, Duration timeout) {
    this.discoveryManager = discoveryManager;
    this.scheduler = scheduler;
    this.taskTimeouts =
        new ExpirationScheduler<>(timeout.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
  }

  public void add(NodeRecordInfo nodeRecordInfo, Runnable successCallback, Runnable failCallback) {
    synchronized (this) {
      if (currentTasks.contains(nodeRecordInfo.getNode().getNodeId())) {
        return;
      }
      currentTasks.add(nodeRecordInfo.getNode().getNodeId());
    }

    scheduler.execute(
        () -> {
          CompletableFuture<Void> ping = discoveryManager.ping(nodeRecordInfo.getNode());
          addTimeout(nodeRecordInfo, ping);
          ping.whenComplete(
              (aVoid, throwable) -> {
                if (throwable != null) {
                  logger.trace(
                      () -> "Liveness check failed for " + nodeRecordInfo.getNode().getNodeId(),
                      throwable);
                  failCallback.run();
                  currentTasks.remove(nodeRecordInfo.getNode().getNodeId());
                } else {
                  successCallback.run();
                  currentTasks.remove(nodeRecordInfo.getNode().getNodeId());
                }
              });
        });
  }

  private void addTimeout(final NodeRecordInfo nodeRecordInfo, final CompletableFuture<Void> ping) {
    taskTimeouts.put(
        nodeRecordInfo.getNode().getNodeId(),
        () -> ping.completeExceptionally(new RuntimeException("Timeout for node check task")));
  }
}
