/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import static org.ethereum.beacon.discovery.schema.NodeStatus.DEAD;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.util.Functions;

/** Manages recurrent node check task(s) */
public class DiscoveryTaskManager {
  private static final Logger LOG = LogManager.getLogger();
  public static final Duration DEFAULT_RETRY_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration DEFAULT_LIVE_CHECK_INTERVAL = Duration.ofSeconds(1);
  static final int STATUS_EXPIRATION_SECONDS = 600;
  private static final int CONCURRENT_LIVENESS_CHECK_LIMIT = 5;
  private static final int RECURSIVE_LOOKUP_INTERVAL_SECONDS = 10;
  private static final int RECURSIVE_SEARCH_QUERY_LIMIT = 15;
  private static final int MAX_RETRIES = 10;
  private final Scheduler scheduler;
  private final Bytes homeNodeId;
  private final LiveCheckTasks liveCheckTasks;
  private final RecursiveLookupTasks recursiveLookupTasks;
  private final NodeTable nodeTable;
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

  private final Consumer<NodeRecord>[] nodeRecordUpdatesConsumers;
  private final Duration liveCheckInterval;
  private boolean resetDead;
  private boolean removeDead;
  private CompletableFuture<Void> liveCheckSchedule;
  private CompletableFuture<Void> recursiveLookupSchedule;
  private CompletableFuture<Void> maintenanceSchedule;

  /**
   * @param discoveryManager Discovery manager
   * @param nodeTable Ethereum node records storage, stores all found nodes
   * @param nodeBucketStorage Node bucket storage. stores only closest nodes in ready-to-answer
   *     format
   * @param homeNode Home node
   * @param scheduler scheduler to run recurrent tasks on
   * @param resetDead Whether to reset dead status of the nodes on start. If set to true, resets its
   *     status at startup and sets number of used retries to 0. Reset applies after remove, so if
   *     remove is on, reset will be applied to 0 nodes
   * @param removeDead Whether to remove nodes that are found dead after several retries
   * @param nodeRecordUpdatesConsumers consumers are executed when nodeRecord is updated with new
   *     sequence number, so it should be updated in nodeSession
   */
  @SafeVarargs
  public DiscoveryTaskManager(
      DiscoveryManager discoveryManager,
      NodeTable nodeTable,
      KBuckets nodeBucketStorage,
      NodeRecord homeNode,
      Scheduler scheduler,
      boolean resetDead,
      boolean removeDead,
      ExpirationSchedulerFactory expirationSchedulerFactory,
      Duration retryTimeout,
      Duration liveCheckInterval,
      Consumer<NodeRecord>... nodeRecordUpdatesConsumers) {
    this.scheduler = scheduler;
    this.nodeTable = nodeTable;
    this.nodeBucketStorage = nodeBucketStorage;
    this.homeNodeId = homeNode.getNodeId();
    this.liveCheckTasks =
        new LiveCheckTasks(discoveryManager, scheduler, expirationSchedulerFactory, retryTimeout);
    this.recursiveLookupTasks =
        new RecursiveLookupTasks(
            discoveryManager, scheduler, expirationSchedulerFactory, retryTimeout);
    this.liveCheckInterval = liveCheckInterval;
    this.resetDead = resetDead;
    this.removeDead = removeDead;
    this.nodeRecordUpdatesConsumers = nodeRecordUpdatesConsumers;
  }

  public synchronized void start() {
    liveCheckSchedule =
        scheduler.executeAtFixedRate(Duration.ZERO, liveCheckInterval, this::liveCheckTask);
    recursiveLookupSchedule =
        scheduler.executeAtFixedRate(
            Duration.ZERO,
            Duration.ofSeconds(RECURSIVE_LOOKUP_INTERVAL_SECONDS),
            this::performSearchForNewPeers);
    maintenanceSchedule =
        scheduler.executeAtFixedRate(Duration.ZERO, liveCheckInterval, this::maintenanceTask);
  }

  public synchronized void stop() {
    safeCancel(liveCheckSchedule);
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

  private void liveCheckTask() {
    List<NodeRecordInfo> nodes = nodeTable.findClosestNodes(homeNodeId, 0);

    // Dead nodes handling
    nodes.stream()
        .filter(DEAD_RULE)
        .forEach(
            deadMarkedNode -> {
              if (removeDead) {
                nodeTable.remove(deadMarkedNode);
              } else {
                nodeTable.save(
                    new NodeRecordInfo(
                        deadMarkedNode.getNode(),
                        deadMarkedNode.getLastRetry(),
                        DEAD,
                        deadMarkedNode.getRetry()));
              }
            });

    // resets dead records
    Stream<NodeRecordInfo> closestNodes = nodes.stream();
    if (resetDead) {
      closestNodes =
          closestNodes.map(
              nodeRecordInfo -> {
                if (DEAD.equals(nodeRecordInfo.getStatus())) {
                  return new NodeRecordInfo(
                      nodeRecordInfo.getNode(), nodeRecordInfo.getLastRetry(), NodeStatus.SLEEP, 0);
                } else {
                  return nodeRecordInfo;
                }
              });
      resetDead = false;
    }

    // Live check task
    closestNodes
        .filter(LIVE_CHECK_NODE_RULE)
        .limit(CONCURRENT_LIVENESS_CHECK_LIMIT)
        .forEach(
            nodeRecord ->
                liveCheckTasks.add(
                    nodeRecord,
                    () ->
                        updateNode(
                            nodeRecord,
                            new NodeRecordInfo(
                                nodeRecord.getNode(), Functions.getTime(), NodeStatus.ACTIVE, 0)),
                    () ->
                        updateNode(
                            nodeRecord,
                            new NodeRecordInfo(
                                nodeRecord.getNode(),
                                Functions.getTime(),
                                NodeStatus.SLEEP,
                                (nodeRecord.getRetry() + 1)))));
  }

  public CompletableFuture<Void> searchForNewPeers() {
    // We wind up with a CompletableFuture<CompletableFuture> so unwrap one level.
    return scheduler.execute(this::performSearchForNewPeers).thenCompose(Function.identity());
  }

  private CompletableFuture<Void> performSearchForNewPeers() {
    return new RecursiveLookupTask(
            nodeTable, this::findNodes, RECURSIVE_SEARCH_QUERY_LIMIT, Bytes32.random())
        .execute();
  }

  private int randomDistance() {
    int distance = Math.max(1, new Random().nextInt(KBuckets.MAXIMUM_BUCKET));
    return distance;
  }

  private CompletableFuture<Void> findNodes(
      final NodeRecordInfo nodeRecordInfo, final int distance) {
    final CompletableFuture<Void> searchResult =
        recursiveLookupTasks.add(nodeRecordInfo.getNode(), distance);
    searchResult.handle(
        (__, error) -> {
          if (error != null) {
            if (error instanceof TimeoutException) {
              updateNode(
                  nodeRecordInfo,
                  new NodeRecordInfo(
                      nodeRecordInfo.getNode(),
                      Functions.getTime(),
                      NodeStatus.SLEEP,
                      (nodeRecordInfo.getRetry() + 1)));
            }
          } else {
            updateNode(
                nodeRecordInfo,
                new NodeRecordInfo(
                    nodeRecordInfo.getNode(), Functions.getTime(), NodeStatus.ACTIVE, 0));
          }
          return null;
        });

    return searchResult;
  }

  void onNodeRecordUpdate(NodeRecord nodeRecord) {
    for (Consumer<NodeRecord> consumer : nodeRecordUpdatesConsumers) {
      consumer.accept(nodeRecord);
    }
  }

  private void updateNode(NodeRecordInfo oldNodeRecordInfo, NodeRecordInfo newNodeRecordInfo) {
    // use node with latest seq known
    if (newNodeRecordInfo.getNode().getSeq().compareTo(oldNodeRecordInfo.getNode().getSeq()) < 0) {
      newNodeRecordInfo =
          new NodeRecordInfo(
              oldNodeRecordInfo.getNode(),
              newNodeRecordInfo.getLastRetry(),
              newNodeRecordInfo.getStatus(),
              newNodeRecordInfo.getRetry());
    } else {
      onNodeRecordUpdate(newNodeRecordInfo.getNode());
    }
    LOG.trace(
        "Updating node {} to status {}",
        newNodeRecordInfo.getNode().getNodeId(),
        newNodeRecordInfo.getStatus());
    nodeTable.save(newNodeRecordInfo);
    nodeBucketStorage.offer(newNodeRecordInfo.getNode());
  }
}
