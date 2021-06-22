/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.KBuckets.LivenessChecker;

public class KBucket {

  static final int K = 16;

  private final LivenessChecker livenessChecker;
  private final Clock clock;

  private final List<BucketEntry> nodes = new ArrayList<>();

  private Optional<BucketEntry> pendingNode = Optional.empty();

  public KBucket(final LivenessChecker livenessChecker, final Clock clock) {
    this.livenessChecker = livenessChecker;
    this.clock = clock;
  }

  public List<NodeRecord> getAllNodes() {
    return nodes.stream().map(BucketEntry::getNode).collect(Collectors.toList());
  }

  public List<NodeRecord> getLiveNodes() {
    return nodes.stream()
        .filter(node -> node.isLive())
        .map(BucketEntry::getNode)
        .collect(Collectors.toList());
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
    if (isFull()) {
      getLastNode().checkLiveness(clock.millis());
      if (pendingNode.isEmpty()) {
        livenessChecker.checkLiveness(node);
      }
    } else {
      final BucketEntry newEntry = new BucketEntry(livenessChecker, node);
      nodes.add(newEntry);
      newEntry.checkLiveness(clock.millis());
    }
  }

  public void onNodeContacted(final NodeRecord node) {
    getEntry(node)
        .ifPresentOrElse(
            existingEntry -> {
              // Move to the start of the bucket
              nodes.remove(existingEntry);
              nodes.add(0, existingEntry.withLastConfirmedTime(clock.millis()));
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
                nodes.add(0, new BucketEntry(livenessChecker, node, clock.millis()));
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
    if (nodes.isEmpty()) {
      return;
    }
    final long currentTime = clock.millis();

    performPendingNodeMaintenance(currentTime);

    final BucketEntry lastNode = getLastNode();
    if (lastNode.hasFailedLivenessCheck(currentTime)) {
      nodes.remove(lastNode);
      pendingNode.ifPresent(
          pendingEntry -> {
            nodes.add(0, pendingEntry);
            pendingNode = Optional.empty();
          });
    } else {
      lastNode.checkLiveness(currentTime);
    }
  }

  private void performPendingNodeMaintenance(final long currentTime) {
    pendingNode.ifPresent(
        pendingEntry -> {
          if (pendingEntry.hasFailedLivenessCheck(currentTime)) {
            pendingNode = Optional.empty();
          } else {
            pendingEntry.checkLiveness(currentTime);
          }
        });
  }

  private BucketEntry getLastNode() {
    return nodes.get(nodes.size() - 1);
  }

  private boolean isFull() {
    return nodes.size() >= K;
  }

  private Optional<BucketEntry> getEntry(final NodeRecord nodeRecord) {
    return nodes.stream().filter(node -> node.getNodeId().equals(nodeRecord.getNodeId())).findAny();
  }
}
