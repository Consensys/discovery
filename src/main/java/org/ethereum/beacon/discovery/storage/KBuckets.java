/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.util.Functions;

public class KBuckets {
  /**
   * Minimum distance we create a bucket for. 0 is our local node record and negative distances
   * aren't allowed.
   */
  private static final int MIN_BUCKET = 1;

  /** Maximum distance we create a bucket for. This is enough to cover all 32 byte node IDs. */
  public static final int MAXIMUM_BUCKET = 256;

  private final LocalNodeRecordStore localNodeRecordStore;
  private final Bytes homeNodeId;
  private final LivenessChecker livenessChecker;
  private final Map<Integer, KBucket> buckets = new HashMap<>();
  private final Clock clock;

  public KBuckets(
      final Clock clock,
      final LocalNodeRecordStore localNodeRecordStore,
      final LivenessChecker livenessChecker) {
    this.clock = clock;
    this.localNodeRecordStore = localNodeRecordStore;
    this.homeNodeId = localNodeRecordStore.getLocalNodeRecord().getNodeId();
    this.livenessChecker = livenessChecker;
  }

  public synchronized Stream<NodeRecord> getLiveNodeRecords(int distance) {
    if (distance == 0) {
      return Stream.of(localNodeRecordStore.getLocalNodeRecord());
    }
    return getBucket(distance).stream().flatMap(bucket -> bucket.getLiveNodes().stream());
  }

  public synchronized Stream<NodeRecord> getAllNodeRecords(int distance) {
    if (distance == 0) {
      return Stream.of(localNodeRecordStore.getLocalNodeRecord());
    }
    return getBucket(distance).stream().flatMap(bucket -> bucket.getAllNodes().stream());
  }

  private Optional<KBucket> getBucket(final int distance) {
    return Optional.ofNullable(buckets.get(distance));
  }

  public synchronized void offer(NodeRecord node) {
    final int distance = Functions.logDistance(homeNodeId, node.getNodeId());
    if (distance > MAXIMUM_BUCKET) {
      // Distance too great, ignore.
      return;
    }
    getOrCreateBucket(distance).ifPresent(bucket -> bucket.offer(node));
  }

  /**
   * Called when we have confirmed the liveness of a node by sending it a request and receiving a
   * valid response back. Must only be called for requests we initiate, not incoming requests from
   * the peer.
   *
   * @param node the node for which liveness was confirmed.
   */
  public synchronized void onNodeContacted(NodeRecord node) {
    final int distance = Functions.logDistance(homeNodeId, node.getNodeId());
    getOrCreateBucket(distance).ifPresent(bucket -> bucket.onLivenessConfirmed(node));
  }

  private Optional<KBucket> getOrCreateBucket(final int distance) {
    if (distance > MAXIMUM_BUCKET || distance < MIN_BUCKET) {
      // Distance too great, ignore.
      return Optional.empty();
    }
    return Optional.of(
        buckets.computeIfAbsent(distance, __ -> new KBucket(livenessChecker, clock)));
  }

  public void performMaintenance(final int distance) {
    getBucket(distance).ifPresent(KBucket::performMaintenance);
  }
}
