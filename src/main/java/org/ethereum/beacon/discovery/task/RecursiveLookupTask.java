/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import static com.google.common.base.Preconditions.checkArgument;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.util.Functions;

public class RecursiveLookupTask {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_CONCURRENT_QUERIES = 3;
  private final KBuckets buckets;
  private final FindNodesAction sendFindNodesRequest;
  private final Bytes targetNodeId;
  private final Set<Bytes> queriedNodeIds = new HashSet<>();
  private final Comparator<NodeRecord> distanceComparator;
  private int availableQuerySlots = MAX_CONCURRENT_QUERIES;
  private int remainingTotalQueryLimit;
  private final CompletableFuture<Collection<NodeRecord>> future = new CompletableFuture<>();
  private final NavigableSet<NodeRecord> foundNodes;

  public RecursiveLookupTask(
      final KBuckets buckets,
      final FindNodesAction sendFindNodesRequest,
      final int totalQueryLimit,
      final Bytes targetNodeId,
      final Bytes homeNodeId) {
    checkArgument(totalQueryLimit > 0, "Must allow positive number of queries");
    this.buckets = buckets;
    this.sendFindNodesRequest = sendFindNodesRequest;
    this.remainingTotalQueryLimit = totalQueryLimit;
    this.targetNodeId = targetNodeId;
    this.distanceComparator =
        Comparator.<NodeRecord, BigInteger>comparing(
                node -> Functions.distance(targetNodeId, node.getNodeId()))
            .reversed()
            .thenComparing(NodeRecord::getNodeId);
    this.foundNodes = new TreeSet<>(distanceComparator);
    // Don't query ourselves
    this.queriedNodeIds.add(homeNodeId);
  }

  public CompletableFuture<Collection<NodeRecord>> execute() {
    sendRequests();
    return future;
  }

  private synchronized void sendRequests() {
    checkArgument(availableQuerySlots >= 0, "Available query slots should never be negative");
    checkArgument(
        remainingTotalQueryLimit >= 0, "Remaining total query limit should never be negative");
    if (availableQuerySlots == 0 || future.isDone()) {
      return;
    }
    if (buckets.containsNode(targetNodeId)) {
      future.complete(foundNodes);
      return;
    }
    final int maxNodesToQuery = Math.min(availableQuerySlots, remainingTotalQueryLimit);
    final Stream<NodeRecord> closestNodesFromBuckets =
        buckets
            .streamClosestNodes(targetNodeId)
            .filter(record -> !queriedNodeIds.contains(record.getNodeId()))
            .limit(maxNodesToQuery);
    Stream.concat(
            closestNodesFromBuckets,
            foundNodes.stream().filter(record -> !queriedNodeIds.contains(record.getNodeId())))
        .sorted(distanceComparator)
        .limit(maxNodesToQuery)
        .forEach(this::queryPeer);
    if (availableQuerySlots == MAX_CONCURRENT_QUERIES) {
      // There are no in-progress queries even after we looked for more to send so must have run out
      // of possible nodes to query or reached the query limit.
      future.complete(foundNodes);
    }
  }

  private void queryPeer(final NodeRecord peer) {
    queriedNodeIds.add(peer.getNodeId());
    availableQuerySlots--;
    remainingTotalQueryLimit--;
    sendFindNodesRequest
        .findNodes(peer, Functions.logDistance(peer.getNodeId(), targetNodeId))
        // Complete async to ensure all queries are sent before any responses are handled
        // Otherwise we can wind up sending too many queries
        .whenCompleteAsync(this::onQueryComplete);
  }

  private synchronized void onQueryComplete(
      final Collection<NodeRecord> nodes, final Throwable error) {
    availableQuerySlots++;
    if (error != null) {
      LOG.debug("Find nodes query failed", error);
    } else {
      foundNodes.addAll(nodes);
    }
    sendRequests();
  }

  public interface FindNodesAction {
    CompletableFuture<Collection<NodeRecord>> findNodes(NodeRecord sendTo, int targetDistance);
  }
}
