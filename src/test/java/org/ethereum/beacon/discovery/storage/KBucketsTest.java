/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.StubClock;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KBucketsTest {

  private final LocalNodeRecordStore localNodeRecordStore = mock(LocalNodeRecordStore.class);
  private final LivenessChecker livenessChecker = mock(LivenessChecker.class);
  private final StubClock clock = new StubClock();
  private final NodeRecord localNode =
      SimpleIdentitySchemaInterpreter.createNodeRecord(
          Bytes32.ZERO, new InetSocketAddress("127.0.0.1", 1));

  private KBuckets buckets;

  @BeforeEach
  void setUp() {
    when(localNodeRecordStore.getLocalNodeRecord()).thenReturn(localNode);
    buckets = new KBuckets(clock, localNodeRecordStore, livenessChecker);
  }

  @Test
  void getNodeRecords_shouldReturnLocalRecordForDistanceZero() {
    assertThat(buckets.getLiveNodeRecords(0)).containsExactly(localNode);
  }

  @Test
  void onNodeContacted_shouldDelegateToCorrectBucket() {
    final int distance = 8;
    final NodeRecord node = createNodeAtDistance(distance);
    buckets.onNodeContacted(node);

    assertThat(buckets.getLiveNodeRecords(distance)).containsExactly(node);
  }

  @Test
  void offer_shouldDelegateToCorrectBucket() {
    final int distance = 8;
    final NodeRecord node = createNodeAtDistance(distance);
    buckets.offer(node);

    assertThat(buckets.getAllNodeRecords(distance)).containsExactly(node);
  }

  @Test
  void shouldGenerateIdsAtCorrectDistance() {
    for (int distance = 1; distance <= 256; distance++) {
      final NodeRecord node = createNodeAtDistance(distance);
      assertThat(Functions.logDistance(localNode.getNodeId(), node.getNodeId()))
          .isEqualTo(distance);
    }
  }

  @Test
  void performMaintenance_shouldPerformMaintenanceOnLeastRecentlyTouchedBucket() {
    final NodeRecord bucket1Node = createNodeAtDistance(1);
    final NodeRecord bucket2Node = createNodeAtDistance(2);
    final NodeRecord bucket3Node = createNodeAtDistance(3);
    clock.advanceTimeMillis(100);
    buckets.onNodeContacted(bucket3Node);
    clock.advanceTimeMillis(100);
    buckets.onNodeContacted(bucket1Node);
    clock.advanceTimeMillis(100);
    buckets.onNodeContacted(bucket2Node);

    // Ensure every node is due to be pinged
    clock.advanceTimeMillis(BucketEntry.MIN_MILLIS_BETWEEN_PINGS * 2);

    // Should update bucket 3 first as it was least recently touched
    buckets.performMaintenance();
    verify(livenessChecker).checkLiveness(bucket3Node);
    verifyNoMoreInteractions(livenessChecker);

    // Then bucket 1
    buckets.performMaintenance();
    verify(livenessChecker).checkLiveness(bucket1Node);
    verifyNoMoreInteractions(livenessChecker);

    // And finally bucket 2
    buckets.performMaintenance();
    verify(livenessChecker).checkLiveness(bucket2Node);
    verifyNoMoreInteractions(livenessChecker);
  }

  @Test
  void streamClosestNodes_shouldIncludeAllNodesInBucket() {
    final List<NodeRecord> nodes = new ArrayList<>();
    for (int distance = 1; distance <= KBuckets.MAXIMUM_BUCKET; distance++) {
      final NodeRecord node = createNodeAtDistance(distance);
      nodes.add(node);
      buckets.onNodeContacted(node);
    }

    assertThat(buckets.streamClosestNodes(Bytes32.ZERO)).containsExactlyElementsOf(nodes);
  }

  @Test
  void streamClosestNodes_shouldIncludeNodesInInitialBucket() {
    final NodeRecord node = createNodeAtDistance(5);
    buckets.onNodeContacted(node);

    assertThat(buckets.streamClosestNodes(node.getNodeId())).contains(node);
  }

  @Test
  void streamClosestNodes_shouldIncludeNodesInMinimumAndMaximumBucket() {
    final NodeRecord closestNode = createNodeAtDistance(1);
    final NodeRecord furthestNode = createNodeAtDistance(KBuckets.MAXIMUM_BUCKET);
    buckets.onNodeContacted(closestNode);
    buckets.onNodeContacted(furthestNode);

    assertThat(buckets.streamClosestNodes(createNodeAtDistance(10).getNodeId()))
        .contains(closestNode, furthestNode);
  }

  @Test
  void streamClosestNodes_shouldNotIncludeLocalNode() {
    assertThat(buckets.streamClosestNodes(localNode.getNodeId())).isEmpty();
  }

  @Test
  void testGetNodeRecordBuckets() {
    final NodeRecord node = createNodeAtDistance(1);
    buckets.offer(node);

    List<List<NodeRecord>> internalBuckets = buckets.getNodeRecordBuckets();
    assertThat(internalBuckets.size()).isEqualTo(1);
    assertThat(internalBuckets.getFirst().getFirst()).isEqualTo(node);
  }

  @Test
  void testDeleteNode() {
    final NodeRecord node = createNodeAtDistance(1);
    buckets.offer(node);
    buckets.deleteNode(node.getNodeId());

    List<List<NodeRecord>> internalBuckets = buckets.getNodeRecordBuckets();
    assertThat(internalBuckets.size()).isEqualTo(1);
    assertThat(internalBuckets.getFirst().size()).isEqualTo(0);
  }

  @Test
  void testDeleteNodeForEmptyBucket() {
    final NodeRecord node = createNodeAtDistance(1);
    // note we are not adding the node to the bucket
    buckets.deleteNode(node.getNodeId());

    List<List<NodeRecord>> internalBuckets = buckets.getNodeRecordBuckets();
    assertThat(internalBuckets.size()).isEqualTo(0);
  }

  @Test
  void testDeleteNodeTwice() {
    final NodeRecord node = createNodeAtDistance(1);
    buckets.offer(node);
    buckets.deleteNode(node.getNodeId());

    List<List<NodeRecord>> internalBuckets = buckets.getNodeRecordBuckets();
    assertThat(internalBuckets.size()).isEqualTo(1);
    assertThat(internalBuckets.getFirst().size()).isEqualTo(0);

    buckets.deleteNode(node.getNodeId());
    internalBuckets = buckets.getNodeRecordBuckets();
    assertThat(internalBuckets.size()).isEqualTo(1);
    assertThat(internalBuckets.getFirst().size()).isEqualTo(0);
  }

  private NodeRecord createNodeAtDistance(final int distance) {
    return TestUtil.createNodeAtDistance(localNode.getNodeId(), distance);
  }
}
