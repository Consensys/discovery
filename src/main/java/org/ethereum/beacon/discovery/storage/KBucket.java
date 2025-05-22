/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.schema.NodeRecord;

class KBucket {

  static final int K = 16;

  private final LivenessChecker livenessChecker;
  private final Clock clock;

  /**
   * The nodes actually in the bucket, ordered by time they were last confirmed as live.
   *
   * <p>Thus the live nodes are at the start of the bucket with not yet confirmed nodes at the end,
   * and the last node in the list is the node least recently confirmed as live that should be the
   * next to check.
   */
  private final List<BucketEntry> nodes = new ArrayList<>();

  /**
   * Stores a node which could not be added because the bucket was full, but is confirmed live and
   * able to be inserted immediately should any node in the bucket be removed.
   */
  private Optional<BucketEntry> pendingNode = Optional.empty();

  private long lastMaintenanceTime = 0;

  public KBucket(final LivenessChecker livenessChecker, final Clock clock) {
    this.livenessChecker = livenessChecker;
    this.clock = clock;
  }

  public void updateStats(final int distance, final BucketStats stats) {
    stats.setBucketStat(distance, (int) streamLiveEntries().count(), nodes.size());
  }

  public List<NodeRecord> getAllNodes() {
    return nodes.stream().map(BucketEntry::getNode).collect(Collectors.toList());
  }

  public List<NodeRecord> getLiveNodes() {
    return streamLiveEntries().map(BucketEntry::getNode).collect(Collectors.toList());
  }

  private Stream<BucketEntry> streamLiveEntries() {
    return nodes.stream().takeWhile(BucketEntry::isLive);
  }

  public Optional<NodeRecord> getPendingNode() {
    return pendingNode.map(BucketEntry::getNode);
  }

  public void offer(final NodeRecord node) {
    performMaintenance();
    getEntry(node)
        .ifPresentOrElse(
            existing -> updateExistingRecord(existing, node), () -> offerNewNode(node));
  }

  private void updateExistingRecord(final BucketEntry existing, final NodeRecord newRecord) {
    if (existing.getNode().getSeq().compareTo(newRecord.getSeq()) >= 0) {
      // New record isn't actually newer, do nothing.
      return;
    }
    nodes.remove(existing);
    final BucketEntry newEntry = new BucketEntry(livenessChecker, newRecord);
    nodes.add(newEntry);
    newEntry.checkLiveness(clock.millis());
  }

  private void offerNewNode(final NodeRecord node) {
    if (livenessChecker.isABadPeer(node)) {
      return;
    }
    if (isFull()) {
      nodes.getLast().checkLiveness(clock.millis());
      if (pendingNode.isEmpty()) {
        livenessChecker.checkLiveness(node);
      }
    } else {
      final BucketEntry newEntry = new BucketEntry(livenessChecker, node);
      nodes.add(newEntry);
      newEntry.checkLiveness(clock.millis());
    }
  }

  public void onLivenessConfirmed(final NodeRecord node) {
    getEntry(node)
        .ifPresentOrElse(
            existingEntry -> {
              // Move to the start of the bucket
              nodes.remove(existingEntry);
              nodes.addFirst(existingEntry.withLastConfirmedTime(clock.millis()));
              performMaintenance();
            },
            () -> {
              if (pendingNode.isPresent()
                  && pendingNode.get().getNodeId().equals(node.getNodeId())) {
                // Update pending node
                pendingNode = Optional.of(pendingNode.get().withLastConfirmedTime(clock.millis()));
              }
              performMaintenance();
              if (isFull()) {
                if (pendingNode.isEmpty()) {
                  pendingNode = Optional.of(new BucketEntry(livenessChecker, node, clock.millis()));
                }
              } else {
                nodes.addFirst(new BucketEntry(livenessChecker, node, clock.millis()));
              }
            });
  }

  /**
   * Performs any pending maintenance on the bucket.
   *
   * <p>If the pending node has not been pinged recently, schedule a ping for it.
   *
   * <p>If the pending node has not responded to the last ping within a reasonable time, remove it.
   *
   * <p>If the last node in the bucket has not been pinged recently, schedule a ping for it.
   *
   * <p>If the last node in the bucket has not responded to the last liveness check within a
   * reasonable time:
   *
   * <p>a. remove it from the bucket.
   *
   * <p>b. if there is a pending node, insert it into the bucket (at appropriate position based on
   * when it was last confirmed as live)
   */
  public void performMaintenance() {
    final long currentTime = clock.millis();
    lastMaintenanceTime = currentTime;
    performPendingNodeMaintenance();

    if (nodes.isEmpty()) {
      return;
    }
    final BucketEntry lastNode = nodes.getLast();
    if (lastNode.hasFailedLivenessCheck(currentTime)) {
      nodes.remove(lastNode);
      pendingNode.ifPresent(
          pendingEntry -> {
            nodes.addFirst(pendingEntry);
            pendingNode = Optional.empty();
          });
    } else {
      lastNode.checkLiveness(currentTime);
    }
  }

  public long getLastMaintenanceTime() {
    return lastMaintenanceTime;
  }

  private void performPendingNodeMaintenance() {
    pendingNode.ifPresent(
        pendingEntry -> {
          final long currentTime = clock.millis();
          if (pendingEntry.hasFailedLivenessCheck(currentTime)) {
            pendingNode = Optional.empty();
          } else {
            pendingEntry.checkLiveness(currentTime);
          }
        });
  }

  private boolean isFull() {
    return nodes.size() >= K;
  }

  private Optional<BucketEntry> getEntry(final NodeRecord nodeRecord) {
    return getEntry(nodeRecord.getNodeId());
  }

  private Optional<BucketEntry> getEntry(final Bytes nodeId) {
    return nodes.stream().filter(node -> node.getNodeId().equals(nodeId)).findAny();
  }

  public Optional<NodeRecord> getNode(final Bytes targetNodeId) {
    return getEntry(targetNodeId).map(BucketEntry::getNode);
  }

  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  public void deleteNode(Bytes nodeId) {
    nodes.removeIf((bucketEntry) -> bucketEntry.getNodeId().equals(nodeId));
    performPendingNodeMaintenance();
    pendingNode.ifPresent(
        pendingEntry -> {
          nodes.addFirst(pendingEntry);
          pendingNode = Optional.empty();
        });
  }
}
