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
import java.util.concurrent.TimeoutException;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/**
 * Sends {@link TaskType#FINDNODE} to closest NodeRecords added via {@link #add(NodeRecord, int)}.
 * Tasks is called failed if timeout is reached and reply from node is not received.
 */
public class RecursiveLookupTasks {
  private final Scheduler scheduler;
  private final DiscoveryManager discoveryManager;
  private final Set<Bytes> currentTasks = Sets.newConcurrentHashSet();
  private final ExpirationScheduler<Bytes> taskTimeouts;

  public RecursiveLookupTasks(
      DiscoveryManager discoveryManager,
      Scheduler scheduler,
      ExpirationSchedulerFactory expirationSchedulerFactory,
      Duration timeout) {
    this.discoveryManager = discoveryManager;
    this.scheduler = scheduler;
    this.taskTimeouts =
        expirationSchedulerFactory.create(timeout.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
  }

  public CompletableFuture<Void> add(NodeRecord nodeRecord, int distance) {
    if (!currentTasks.add(nodeRecord.getNodeId())) {
      return CompletableFuture.failedFuture(new IllegalStateException("Already querying node"));
    }

    final CompletableFuture<Void> result = new CompletableFuture<>();
    scheduler.execute(
        () -> {
          CompletableFuture<Void> request = discoveryManager.findNodes(nodeRecord, distance);
          addTimeout(nodeRecord, request);
          request.whenComplete(
              (aVoid, throwable) -> {
                currentTasks.remove(nodeRecord.getNodeId());
                if (throwable != null) {
                  result.completeExceptionally(throwable);
                } else {
                  result.complete(null);
                }
              });
        });
    return result;
  }

  private void addTimeout(final NodeRecord nodeRecord, final CompletableFuture<Void> retry) {
    taskTimeouts.put(
        nodeRecord.getNodeId(),
        () ->
            retry.completeExceptionally(
                new TimeoutException("Timeout for node recursive lookup task")));
  }
}
