/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.KBuckets;

/** Manages recurrent node check task(s) */
public class DiscoveryTaskManager {

  public static final Duration DEFAULT_RETRY_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration DEFAULT_LIVE_CHECK_INTERVAL = Duration.ofSeconds(1);
  public static final Duration DEFAULT_RECURSIVE_LOOKUP_INTERVAL_SECONDS = Duration.ofSeconds(10);
  private static final int RECURSIVE_SEARCH_QUERY_LIMIT = 15;
  private final Bytes homeNodeId;
  private final Scheduler scheduler;
  private final RecursiveLookupTasks recursiveLookupTasks;
  private final KBuckets nodeBucketStorage;

  private final Duration recursiveLookupIntervalSeconds;
  private final Duration liveCheckInterval;
  private CompletableFuture<Void> recursiveLookupSchedule;
  private CompletableFuture<Void> maintenanceSchedule;

  /**
   * @param discoveryManager Discovery manager
   * @param nodeBucketStorage Node bucket storage. stores only closest nodes in ready-to-answer
   *     format
   * @param scheduler scheduler to run recurrent tasks on
   */
  public DiscoveryTaskManager(
      DiscoveryManager discoveryManager,
      Bytes homeNodeId,
      KBuckets nodeBucketStorage,
      Scheduler scheduler,
      ExpirationSchedulerFactory expirationSchedulerFactory,
      Duration recursiveLookupIntervalSeconds,
      Duration retryTimeout,
      Duration liveCheckInterval) {
    this.homeNodeId = homeNodeId;
    this.scheduler = scheduler;
    this.nodeBucketStorage = nodeBucketStorage;
    this.recursiveLookupTasks =
        new RecursiveLookupTasks(
            discoveryManager, scheduler, expirationSchedulerFactory, retryTimeout);
    this.recursiveLookupIntervalSeconds = recursiveLookupIntervalSeconds;
    this.liveCheckInterval = liveCheckInterval;
  }

  public synchronized void start() {
    recursiveLookupSchedule =
        scheduler.executeAtFixedRate(
            Duration.ZERO, recursiveLookupIntervalSeconds, this::performSearchForNewPeers);
    maintenanceSchedule =
        scheduler.executeAtFixedRate(
            Duration.ZERO, liveCheckInterval, nodeBucketStorage::performMaintenance);
  }

  public synchronized void stop() {
    safeCancel(recursiveLookupSchedule);
    safeCancel(maintenanceSchedule);
  }

  private void safeCancel(final Future<?> future) {
    if (future != null) {
      future.cancel(true);
    }
  }

  public CompletableFuture<Collection<NodeRecord>> searchForNewPeers() {
    // We wind up with a CompletableFuture<CompletableFuture> so unwrap one level.
    return scheduler.execute(this::performSearchForNewPeers).thenCompose(Function.identity());
  }

  private CompletableFuture<Collection<NodeRecord>> performSearchForNewPeers() {
    return new RecursiveLookupTask(
            nodeBucketStorage,
            this::findNodes,
            RECURSIVE_SEARCH_QUERY_LIMIT,
            Bytes32.random(),
            homeNodeId)
        .execute();
  }

  private CompletableFuture<Collection<NodeRecord>> findNodes(
      final NodeRecord nodeRecord, final int targetDistance) {
    final List<Integer> distances = new ArrayList<>();
    for (int distance = targetDistance;
        distance <= Math.min(KBuckets.MAXIMUM_BUCKET, targetDistance + 3);
        distance++) {
      distances.add(distance);
    }
    return recursiveLookupTasks.add(nodeRecord, distances);
  }
}
