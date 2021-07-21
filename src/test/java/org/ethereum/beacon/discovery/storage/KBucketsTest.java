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
import java.util.BitSet;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.StubClock;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KBucketsTest {

  private static final int ID_SIZE = 32;
  private final LocalNodeRecordStore localNodeRecordStore = mock(LocalNodeRecordStore.class);
  private final LivenessChecker livenessChecker = mock(LivenessChecker.class);
  private final StubClock clock = new StubClock();
  private final NodeRecord localNode =
      SimpleIdentitySchemaInterpreter.createNodeRecord(
          Bytes.wrap(new byte[ID_SIZE]), new InetSocketAddress("127.0.0.1", 1));

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

  private NodeRecord createNodeAtDistance(final int distance) {
    final BitSet bits = new BitSet(ID_SIZE * Byte.SIZE);
    bits.set(distance - 1);
    final byte[] targetNodeId = new byte[ID_SIZE];
    final byte[] src = bits.toByteArray();
    System.arraycopy(src, 0, targetNodeId, 0, src.length);
    final Bytes nodeId = Bytes.wrap(targetNodeId).reverse();
    return SimpleIdentitySchemaInterpreter.createNodeRecord(
        nodeId, new InetSocketAddress("127.0.0.1", 2));
  }
}
