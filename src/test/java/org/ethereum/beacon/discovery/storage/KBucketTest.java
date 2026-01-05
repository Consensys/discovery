/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.storage.BucketEntry.MIN_MILLIS_BETWEEN_PINGS;
import static org.ethereum.beacon.discovery.storage.BucketEntry.PING_TIMEOUT_MILLIS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.StubClock;
import org.ethereum.beacon.discovery.crypto.InMemoryNodeKeyService;
import org.ethereum.beacon.discovery.crypto.NodeKeyService;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

class KBucketTest {

  private static final NodeKeyService SECURITY_MODULE =
      InMemoryNodeKeyService.create(Functions.randomKeyPair().secretKey());

  private final LivenessChecker livenessChecker = mock(LivenessChecker.class);

  private final StubClock clock = new StubClock();
  private final KBucket bucket = new KBucket(livenessChecker, clock);

  private int lastNodeId = 0;

  @Test
  void offer_shouldAddNodeAndCheckLivenessWhenTheBucketIsEmpty() {
    final NodeRecord node = createNewNodeRecord();
    bucket.offer(node);

    assertThat(bucket.getAllNodes()).containsExactly(node);
    assertThat(bucket.getLiveNodes()).isEmpty();

    verify(livenessChecker).checkLiveness(node);
  }

  @Test
  void offer_shouldAddNodeAndCheckLivenessWhenBucketIsNotYetFull() {
    final NodeRecord node1 = createNewNodeRecord();
    final NodeRecord node2 = createNewNodeRecord();
    final NodeRecord node3 = createNewNodeRecord();
    bucket.offer(node1);
    bucket.offer(node2);
    bucket.offer(node3);

    assertThat(bucket.getAllNodes()).containsExactly(node1, node2, node3);
    assertThat(bucket.getLiveNodes()).isEmpty();

    verify(livenessChecker).checkLiveness(node1);
    verify(livenessChecker).checkLiveness(node2);
    verify(livenessChecker).checkLiveness(node3);
  }

  @Test
  void offer_shouldPingLastNodeInBucketAndNewNodeWhenBucketFullWithNoPending() {
    final NodeRecord lastRecordInBucket = fillBucketWithLiveNodes();

    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);

    final NodeRecord newNode = createNewNodeRecord();
    bucket.offer(newNode);

    // Should trigger a new liveness check for the last item in the bucket
    verify(livenessChecker, times(2)).checkLiveness(lastRecordInBucket);

    // Should check the new node to see if it can be used as the pending node
    verify(livenessChecker).checkLiveness(newNode);
    // But doesn't add it to the bucket
    assertThat(bucket.getAllNodes()).doesNotContain(newNode);
  }

  @Test
  void offer_shouldPingLastNodeInBucketButNotNewNodeWhenBucketFullWithPendingEntry() {
    final NodeRecord lastRecordInBucket = fillBucketWithLiveNodes();

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);

    final NodeRecord newNode = createNewNodeRecord();
    bucket.offer(newNode);

    // We already have a pending node so ignore the new node
    verify(livenessChecker, never()).checkLiveness(newNode);

    // Should still trigger a new liveness check for the last item in the bucket
    verify(livenessChecker, times(2)).checkLiveness(lastRecordInBucket);

    // But doesn't add it to the bucket
    assertThat(bucket.getAllNodes()).doesNotContain(newNode);
  }

  @Test
  void offer_shouldUpdateExistingEntryInBucket() {
    final NodeRecord nodeSeq1 = createNewNodeRecord();
    final NodeRecord nodeSeq2 =
        nodeSeq1.withUpdatedCustomField("hello", Bytes.fromHexString("0x1234"), SECURITY_MODULE);

    bucket.offer(nodeSeq1);
    bucket.offer(nodeSeq2);
    verify(livenessChecker).checkLiveness(nodeSeq1);
    verify(livenessChecker).checkLiveness(nodeSeq2);

    assertThat(bucket.getAllNodes()).containsExactly(nodeSeq2);
  }

  @Test
  void offer_shouldNotUpdateExistingEntryWhenNewRecordIsOlder() {
    final NodeRecord nodeSeq1 = createNewNodeRecord();
    final NodeRecord nodeSeq2 =
        nodeSeq1.withUpdatedCustomField("record", Bytes.fromHexString("0x1234"), SECURITY_MODULE);

    bucket.offer(nodeSeq2);
    bucket.offer(nodeSeq1);
    verify(livenessChecker).checkLiveness(nodeSeq2);
    verify(livenessChecker, never()).checkLiveness(nodeSeq1);

    assertThat(bucket.getAllNodes()).containsExactly(nodeSeq2);
  }

  @Test
  void offer_shouldNotUpdateExistingEntryWhenNewRecordIsSameAge() {
    final NodeRecord nodeSeq1 = createNewNodeRecord();

    bucket.offer(nodeSeq1);
    bucket.offer(nodeSeq1);

    // Only checks liveness once
    verify(livenessChecker).checkLiveness(nodeSeq1);
    assertThat(bucket.getAllNodes()).containsExactly(nodeSeq1);
  }

  @Test
  void offer_shouldMoveUpdatedNodeToEndOfBucket() {
    final NodeRecord otherNode = createNewNodeRecord();
    final NodeRecord nodeSeq1 = createNewNodeRecord();
    final NodeRecord nodeSeq2 =
        nodeSeq1.withUpdatedCustomField("hello", Bytes.fromHexString("0x1234"), SECURITY_MODULE);

    bucket.offer(nodeSeq1);
    bucket.offer(otherNode);

    assertThat(bucket.getAllNodes()).containsExactly(nodeSeq1, otherNode);

    bucket.offer(nodeSeq2);
    assertThat(bucket.getAllNodes()).containsExactly(otherNode, nodeSeq2);
  }

  @Test
  void offer_shouldRemovePendingNodeIfTimedOutBeforeConsideringNewNode() {
    fillBucketWithLiveNodes();
    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);

    // Pending node times out
    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);
    confirmNodesInBucketAsLive();
    bucket.performMaintenance();
    verify(livenessChecker).checkLiveness(pendingNode);
    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    // Timed out pending node is removed on the next call to offer
    final NodeRecord newNode = createNewNodeRecord();
    bucket.offer(newNode);
    assertThat(bucket.getPendingNode()).isEmpty();
    verify(livenessChecker).checkLiveness(newNode);
  }

  @Test
  void offer_shouldReplaceLastNodeIfItAndPendingNodeAreTimedOut() {
    final NodeRecord lastNodeInBucket = fillBucketWithLiveNodes();
    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);

    // Pending node times out
    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);
    bucket.getAllNodes().stream()
        .filter(node -> !node.equals(lastNodeInBucket))
        .forEach(bucket::onLivenessConfirmed);
    bucket.performMaintenance();
    verify(livenessChecker).checkLiveness(pendingNode);
    verify(livenessChecker, times(2)).checkLiveness(lastNodeInBucket);
    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);
    assertThat(bucket.getPendingNode()).contains(pendingNode);
    assertThat(bucket.getAllNodes()).contains(lastNodeInBucket);

    // Timed out pending node is removed on the next call to offer
    final NodeRecord newNode = createNewNodeRecord();
    bucket.offer(newNode);
    assertThat(bucket.getPendingNode()).isEmpty();
    assertThat(bucket.getAllNodes()).contains(newNode).doesNotContain(lastNodeInBucket);
    verify(livenessChecker).checkLiveness(newNode);
  }

  @Test
  void onNodeContacted_shouldMoveExistingNodeToFrontOfBucket() {
    final NodeRecord lastNode = fillBucket();

    bucket.onLivenessConfirmed(lastNode);

    assertThat(bucket.getAllNodes()).startsWith(lastNode).containsOnlyOnce(lastNode);
  }

  @Test
  void onNodeContacted_shouldAddNodeToFrontOfBucketWhenNotFull() {
    final NodeRecord node1 = createNewNodeRecord();
    final NodeRecord node2 = createNewNodeRecord();

    bucket.onLivenessConfirmed(node1);
    assertThat(bucket.getAllNodes()).containsExactly(node1);

    bucket.onLivenessConfirmed(node2);
    assertThat(bucket.getAllNodes()).containsExactly(node2, node1);
  }

  @Test
  void onNodeContacted_shouldIgnoreNodeWhenBucketFullAndPendingEntryAlreadyExists() {
    fillBucket();

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    final NodeRecord node2 = createNewNodeRecord();
    bucket.onLivenessConfirmed(node2);
    assertThat(bucket.getAllNodes()).doesNotContain(node2);
    assertThat(bucket.getPendingNode()).contains(pendingNode);
  }

  @Test
  void onNodeContacted_shouldReplacePendingEntryIfItHasTimedOut() {
    fillBucket();
    confirmNodesInBucketAsLive();

    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    // Timeout pending node
    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);
    bucket.performMaintenance();
    confirmNodesInBucketAsLive();
    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);

    // New node should replace the timed out pending node
    final NodeRecord newNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(newNode);

    assertThat(bucket.getPendingNode()).contains(newNode);
    assertThat(bucket.getAllNodes()).doesNotContain(pendingNode);
  }

  @Test
  void onNodeContacted_shouldReplaceLastNodeIfItHasTimedOut() {
    final NodeRecord lastNode = fillBucket();

    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);

    final NodeRecord newNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(newNode);

    assertThat(bucket.getAllNodes()).contains(newNode).doesNotContain(lastNode);
    assertThat(bucket.getPendingNode()).isEmpty();
  }

  @Test
  void onNodeContacted_shouldReplaceTimedOutLastNodeWithPendingAndUseNewNodeAsPending() {
    fillBucketWithLiveNodes();

    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    // Timeout last node
    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);
    bucket.performMaintenance();

    // Everything except the last node responds to the ping
    final NodeRecord lastNode = getLastNodeInBucket();
    bucket.getAllNodes().stream()
        .filter(node -> !node.equals(lastNode))
        .forEach(bucket::onLivenessConfirmed);
    bucket.onLivenessConfirmed(pendingNode);

    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);

    // Pending node should replace the timed out last node and new node becomes pending
    final NodeRecord newNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(newNode);

    assertThat(bucket.getAllNodes()).contains(pendingNode).doesNotContain(lastNode);
    assertThat(bucket.getPendingNode()).contains(newNode);
  }

  @Test
  void onNodeContacted_shouldUpdateLivenessConfirmationTimeForPendingNode() {
    fillBucket();
    confirmNodesInBucketAsLive();

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);
    verify(livenessChecker, never()).checkLiveness(pendingNode);

    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);
    bucket.onLivenessConfirmed(pendingNode);
    // No need to ping pending node because we know its live
    verify(livenessChecker, never()).checkLiveness(pendingNode);

    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS - 1);
    bucket.performMaintenance();
    // Still not need to ping because it the last contact updated the time
    verify(livenessChecker, never()).checkLiveness(pendingNode);
  }

  @Test
  void performMaintenance_shouldRemoveLastNodeInBucketWhenItHasFailedToRespondToPingForTooLong() {
    final NodeRecord lastRecordInBucket = fillBucket();

    // First offered node schedules a ping for the last node and the new node
    final NodeRecord newNode = createNewNodeRecord();
    bucket.offer(newNode);
    verify(livenessChecker).checkLiveness(lastRecordInBucket);
    verify(livenessChecker).checkLiveness(newNode);

    clock.advanceTimeMillis(BucketEntry.PING_TIMEOUT_MILLIS);

    // When bucket is randomly selected to perform maintenance, the last record is removed
    bucket.performMaintenance();

    assertThat(bucket.getAllNodes()).doesNotContain(lastRecordInBucket);
  }

  @Test
  void performMaintenance_shouldInsertPendingNodeWhenLastNodeRemoved() {
    final NodeRecord lastRecordInBucket = fillBucket();

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    // First offered node schedules a ping for the last node and the new node
    bucket.offer(pendingNode);
    verify(livenessChecker).checkLiveness(lastRecordInBucket);

    clock.advanceTimeMillis(BucketEntry.PING_TIMEOUT_MILLIS);

    // When bucket is randomly selected to perform maintenance, the last record is removed
    bucket.performMaintenance();

    assertThat(bucket.getAllNodes()).doesNotContain(lastRecordInBucket);
    // Newest inserted node goes first in the bucket
    assertThat(bucket.getAllNodes()).startsWith(pendingNode);
    assertThat(bucket.getPendingNode()).isEmpty();
  }

  @Test
  void performMaintenance_shouldCheckLivenessOfPendingNodeIfRequired() {
    fillBucket();

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);

    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);

    bucket.performMaintenance();
    verify(livenessChecker).checkLiveness(pendingNode);
  }

  @Test
  void performMaintenance_shouldNotCheckLivenessOfPendingNodeWhenRecentlyConfirmed() {
    fillBucket();

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);

    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS - 1);

    bucket.performMaintenance();
    verify(livenessChecker, never()).checkLiveness(pendingNode);
  }

  @Test
  void performMaintenance_shouldRemovePendingNodeIfPingTimedOut() {
    fillBucket();
    confirmNodesInBucketAsLive(); // Existing nodes stay live

    final NodeRecord pendingNode = createNewNodeRecord();
    bucket.onLivenessConfirmed(pendingNode);

    clock.advanceTimeMillis(MIN_MILLIS_BETWEEN_PINGS);
    confirmNodesInBucketAsLive(); // Existing nodes stay live
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    bucket.performMaintenance();
    verify(livenessChecker).checkLiveness(pendingNode);
    assertThat(bucket.getPendingNode()).contains(pendingNode);

    clock.advanceTimeMillis(PING_TIMEOUT_MILLIS);
    bucket.performMaintenance();
    assertThat(bucket.getPendingNode()).isEmpty();
  }

  @Test
  void getLiveNodes_shouldOnlyReturnedConfirmedLiveNodes() {
    final NodeRecord node1 = createNewNodeRecord();
    final NodeRecord node2 = createNewNodeRecord();
    final NodeRecord node3 = createNewNodeRecord();
    final NodeRecord node4 = createNewNodeRecord();

    bucket.offer(node1);
    bucket.onLivenessConfirmed(node2);
    bucket.offer(node3);
    bucket.onLivenessConfirmed(node4);
    bucket.onLivenessConfirmed(node3);

    assertThat(bucket.getAllNodes()).containsExactly(node3, node4, node2, node1);
    assertThat(bucket.getLiveNodes()).containsExactly(node3, node4, node2);
  }

  @Test
  void testDeleteNode() {
    final NodeRecord nodeToBeDeleted = fillBucket();
    final NodeRecord pendingNode = createNewNodeRecord();

    bucket.offer(pendingNode);
    bucket.onLivenessConfirmed(pendingNode);
    bucket.deleteNode(nodeToBeDeleted.getNodeId());

    assertThat(bucket.getAllNodes()).doesNotContain(nodeToBeDeleted);
    assertThat(bucket.getAllNodes()).contains(pendingNode);
  }

  private void confirmNodesInBucketAsLive() {
    bucket.getAllNodes().forEach(bucket::onLivenessConfirmed);
  }

  private NodeRecord fillBucket() {
    for (int i = 0; i < KBucket.K - 1; i++) {
      bucket.offer(createNewNodeRecord());
    }
    final NodeRecord lastRecord = createNewNodeRecord();
    bucket.offer(lastRecord);
    return lastRecord;
  }

  private NodeRecord createNewNodeRecord() {
    lastNodeId++;
    return SimpleIdentitySchemaInterpreter.createNodeRecord(lastNodeId);
  }

  private NodeRecord getLastNodeInBucket() {
    return bucket.getAllNodes().getLast();
  }

  private NodeRecord fillBucketWithLiveNodes() {
    fillBucket();
    confirmNodesInBucketAsLive();
    return getLastNodeInBucket();
  }
}
