/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
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
  public static final Duration DEFAULT_RECURSIVE_LOOKUP_INTERVAL = Duration.ofSeconds(10);
  private static final int RECURSIVE_SEARCH_QUERY_LIMIT = 15;
  private static final int NUM_DISTANCES_TO_QUERY = 3;
  private final Bytes homeNodeId;
  private final Scheduler scheduler;
  private final RecursiveLookupTasks recursiveLookupTasks;
  private final KBuckets nodeBucketStorage;

  private final Duration recursiveLookupInterval;
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
      Duration recursiveLookupInterval,
      Duration retryTimeout,
      Duration liveCheckInterval) {
    this.homeNodeId = homeNodeId;
    this.scheduler = scheduler;
    this.nodeBucketStorage = nodeBucketStorage;
    this.recursiveLookupTasks =
        new RecursiveLookupTasks(
            discoveryManager, scheduler, expirationSchedulerFactory, retryTimeout);
    this.recursiveLookupInterval = recursiveLookupInterval;
    this.liveCheckInterval = liveCheckInterval;
  }

  public synchronized void start() {
    recursiveLookupSchedule =
        scheduler.executeAtFixedRate(
            Duration.ZERO, recursiveLookupInterval, this::performSearchForNewPeers);
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

  @VisibleForTesting
  static List<Integer> getClosestDistances(final int targetDistance) {
    final List<Integer> distances = new ArrayList<>();
    checkArgument(targetDistance >= KBuckets.MINIMUM_BUCKET, "invalid target distance");
    checkArgument(targetDistance <= KBuckets.MAXIMUM_BUCKET, "invalid target distance");
    distances.add(targetDistance);

    // Until we get the number of distances we want, add a distance from the right of the target
    // distance, then add a distance from the left. These will be the closest nodes.
    for (int offset = 1; distances.size() < NUM_DISTANCES_TO_QUERY; offset++) {
      // If possible, query offset the right.
      int right = targetDistance + offset;
      if (right <= KBuckets.MAXIMUM_BUCKET) {
        distances.add(right);
      }

      // This check is necessary if NUM_DISTANCES_TO_QUERY is an even number or the target distance
      // is close to the minimum/maximum boundary.
      if (distances.size() == NUM_DISTANCES_TO_QUERY) {
        break;
      }

      // If possible, query offset to the left.
      int left = targetDistance - offset;
      if (left >= KBuckets.MINIMUM_BUCKET) {
        distances.add(left);
      }
    }

    return distances;
  }

  private CompletableFuture<Collection<NodeRecord>> findNodes(
      final NodeRecord nodeRecord, final int targetDistance) {
    return recursiveLookupTasks.add(nodeRecord, getClosestDistances(targetDistance));
  }
}
