/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import static org.ethereum.beacon.discovery.schema.NodeStatus.DEAD;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.util.Functions;

/** Manages recurrent node check task(s) */
public class DiscoveryTaskManager {
  private static final Logger LOG = LogManager.getLogger();
  public static final Duration DEFAULT_RETRY_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration DEFAULT_LIVE_CHECK_INTERVAL = Duration.ofSeconds(1);
  static final int STATUS_EXPIRATION_SECONDS = 600;
  private static final int RECURSIVE_LOOKUP_INTERVAL_SECONDS = 10;
  private static final int RECURSIVE_SEARCH_QUERY_LIMIT = 15;
  private static final int MAX_RETRIES = 10;
  private final Scheduler scheduler;
  private final RecursiveLookupTasks recursiveLookupTasks;
  private final KBuckets nodeBucketStorage;
  /**
   * Checks whether {@link NodeRecord} is ready for alive status check. Plus, marks records as DEAD
   * if there were a lot of unsuccessful retries to get reply from node.
   *
   * <p>We don't need to recheck the node if
   *
   * <ul>
   *   <li>Node is ACTIVE and last connection retry was not too much time ago
   *   <li>Node is marked as {@link NodeStatus#DEAD}
   *   <li>Node is not ACTIVE but last connection retry was "seconds ago"
   * </ul>
   *
   * <p>In all other cases method returns true, meaning node is ready for ping check
   */
  @SuppressWarnings("UnnecessaryLambda")
  private static final Predicate<NodeRecordInfo> LIVE_CHECK_NODE_RULE =
      nodeRecord -> {
        long currentTime = Functions.getTime();
        if (nodeRecord.getStatus() == NodeStatus.ACTIVE
            && nodeRecord.getLastRetry() > currentTime - STATUS_EXPIRATION_SECONDS) {
          return false; // no need to rediscover
        }
        if (DEAD.equals(nodeRecord.getStatus())) {
          return false; // node looks dead but we are keeping its records for some reason
        }
        if ((currentTime - nodeRecord.getLastRetry())
            < (nodeRecord.getRetry() * nodeRecord.getRetry())) {
          return false; // too early for retry
        }

        return true;
      };

  /**
   * Checks whether {@link org.ethereum.beacon.discovery.schema.NodeRecord} is ready for FINDNODE
   * query which expands the list of all known nodes.
   *
   * <p>Node is eligible if
   *
   * <ul>
   *   <li>Node is ACTIVE and last connection retry was not too much time ago
   * </ul>
   */
  @SuppressWarnings("UnnecessaryLambda")
  static final Predicate<NodeRecordInfo> RECURSIVE_LOOKUP_NODE_RULE =
      nodeRecord ->
          nodeRecord.getStatus() == NodeStatus.ACTIVE
              && nodeRecord.getLastRetry() > Functions.getTime() - STATUS_EXPIRATION_SECONDS;

  /** Checks whether node is eligible to be considered as dead */
  @SuppressWarnings("UnnecessaryLambda")
  private static final Predicate<NodeRecordInfo> DEAD_RULE =
      nodeRecord -> nodeRecord.getRetry() >= MAX_RETRIES;

  private final Duration liveCheckInterval;
  private CompletableFuture<Void> recursiveLookupSchedule;
  private CompletableFuture<Void> maintenanceSchedule;

  /**
   * @param discoveryManager Discovery manager
   * @param nodeBucketStorage Node bucket storage. stores only closest nodes in ready-to-answer
   *     format
   * @param homeNode Home node
   * @param scheduler scheduler to run recurrent tasks on
   */
  public DiscoveryTaskManager(
      DiscoveryManager discoveryManager,
      KBuckets nodeBucketStorage,
      NodeRecord homeNode,
      Scheduler scheduler,
      ExpirationSchedulerFactory expirationSchedulerFactory,
      Duration retryTimeout,
      Duration liveCheckInterval) {
    this.scheduler = scheduler;
    this.nodeBucketStorage = nodeBucketStorage;
    this.recursiveLookupTasks =
        new RecursiveLookupTasks(
            discoveryManager, scheduler, expirationSchedulerFactory, retryTimeout);
    this.liveCheckInterval = liveCheckInterval;
  }

  public synchronized void start() {
    recursiveLookupSchedule =
        scheduler.executeAtFixedRate(
            Duration.ZERO,
            Duration.ofSeconds(RECURSIVE_LOOKUP_INTERVAL_SECONDS),
            this::performSearchForNewPeers);
    maintenanceSchedule =
        scheduler.executeAtFixedRate(Duration.ZERO, liveCheckInterval, this::maintenanceTask);
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

  private void maintenanceTask() {
    final int distance = randomDistance();
    nodeBucketStorage.performMaintenance(distance);
  }

  public CompletableFuture<Void> searchForNewPeers() {
    // We wind up with a CompletableFuture<CompletableFuture> so unwrap one level.
    return scheduler.execute(this::performSearchForNewPeers).thenCompose(Function.identity());
  }

  private CompletableFuture<Void> performSearchForNewPeers() {
    return new RecursiveLookupTask(
            nodeBucketStorage, this::findNodes, RECURSIVE_SEARCH_QUERY_LIMIT, Bytes32.random())
        .execute();
  }

  private int randomDistance() {
    return Math.max(1, new Random().nextInt(KBuckets.MAXIMUM_BUCKET));
  }

  private CompletableFuture<Void> findNodes(final NodeRecord nodeRecord, final int distance) {
    return recursiveLookupTasks.add(nodeRecord, distance);
  }
}
