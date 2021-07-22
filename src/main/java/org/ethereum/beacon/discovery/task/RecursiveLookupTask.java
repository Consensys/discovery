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
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.util.Functions;

public class RecursiveLookupTask {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_CONCURRENT_QUERIES = 3;
  private final FindNodesAction sendFindNodesRequest;
  private final Bytes targetNodeId;
  private final Set<Bytes> queriedNodeIds = new HashSet<>();
  private final Comparator<NodeRecord> distanceComparator;
  private final NodeTable nodeTable;
  private int availableQuerySlots = MAX_CONCURRENT_QUERIES;
  private int remainingTotalQueryLimit;
  private final CompletableFuture<Collection<NodeRecord>> future = new CompletableFuture<>();
  private final NavigableSet<NodeRecord> foundNodes;

  public RecursiveLookupTask(
      final NodeTable nodeTable,
      final FindNodesAction sendFindNodesRequest,
      final int totalQueryLimit,
      final Bytes targetNodeId,
      final Bytes homeNodeId) {
    this.nodeTable = nodeTable;
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
    if (availableQuerySlots == 0 || future.isDone()) {
      return;
    }
    if (nodeTable.getNode(targetNodeId).isPresent()) {
      future.complete(foundNodes);
      return;
    }
    final Stream<NodeRecordInfo> closestNodesFromBuckets =
        nodeTable
            .streamClosestNodes(targetNodeId, 0)
            .filter(DiscoveryTaskManager.RECURSIVE_LOOKUP_NODE_RULE)
            .filter(node -> !queriedNodeIds.contains(node.getNode().getNodeId()))
            .limit(Math.min(availableQuerySlots, remainingTotalQueryLimit));
    Stream.concat(closestNodesFromBuckets, foundNodes.stream().map(NodeRecordInfo::createDefault))
        .sorted((o1, o2) -> distanceComparator.compare(o1.getNode(), o2.getNode()))
        .limit(Math.min(availableQuerySlots, remainingTotalQueryLimit))
        .forEach(this::queryPeer);
    if (availableQuerySlots == MAX_CONCURRENT_QUERIES) {
      // There are no in-progress queries even after we looked for more to send so must have run out
      // of possible nodes to query or reached the query limit.
      future.complete(foundNodes);
    }
  }

  private void queryPeer(final NodeRecordInfo peer) {
    queriedNodeIds.add(peer.getNode().getNodeId());
    availableQuerySlots--;
    remainingTotalQueryLimit--;
    sendFindNodesRequest
        .findNodes(peer, Functions.logDistance(peer.getNode().getNodeId(), targetNodeId))
        .whenComplete(
            (nodes, error) -> {
              if (error != null) {
                LOG.debug("Failed to query " + peer.getNode().getNodeId(), error);
              }
              synchronized (RecursiveLookupTask.this) {
                availableQuerySlots++;
                foundNodes.addAll(nodes);
                sendRequests();
              }
            });
  }

  public interface FindNodesAction {
    CompletableFuture<Collection<NodeRecord>> findNodes(NodeRecordInfo sendTo, int distance);
  }
}
