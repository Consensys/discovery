/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.liveness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.liveness.LivenessChecker.Pinger;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LivenessCheckerTest {
  private int lastNodeId = 0;

  private final Pinger pinger = mock(Pinger.class);
  private final Map<NodeRecord, CompletableFuture<Void>> pingResults = new HashMap<>();

  private final LivenessChecker livenessChecker = new LivenessChecker();

  @BeforeEach
  void setUp() {
    livenessChecker.setPinger(pinger);
    when(pinger.ping(any()))
        .thenAnswer(
            invocation -> {
              final CompletableFuture<Void> result = new CompletableFuture<>();
              pingResults.put(invocation.getArgument(0), result);
              return result;
            });
  }

  @Test
  void shouldPingNodeImmediatelyIfNoChecksInProgress() {
    final NodeRecord node = createNewNodeRecord();
    livenessChecker.checkLiveness(node);

    verify(pinger).ping(node);
  }

  @Test
  void shouldQueuePingsOnceConcurrentLimitIsReached() {
    final List<NodeRecord> initialPings = sendMaxConcurrentPings();
    assertThat(pingResults).containsOnlyKeys(initialPings);

    final NodeRecord queuedNode = createNewNodeRecord();
    livenessChecker.checkLiveness(queuedNode);
    // Should not ping immediately
    assertThat(pingResults).doesNotContainKey(queuedNode);

    // But should ping when any of the initial pings completes
    pingCompleted(initialPings.get(1));
    assertThat(pingResults).containsKey(queuedNode);
  }

  @Test
  void shouldQueueMultipleNodesToPing() {
    final List<NodeRecord> pingedNodes = sendMaxConcurrentPings();

    final NodeRecord node1 = createNewNodeRecord();
    final NodeRecord node2 = createNewNodeRecord();
    final NodeRecord node3 = createNewNodeRecord();
    final NodeRecord node4 = createNewNodeRecord();
    livenessChecker.checkLiveness(node1);
    livenessChecker.checkLiveness(node2);
    livenessChecker.checkLiveness(node3);
    livenessChecker.checkLiveness(node4);

    assertThat(pingResults).doesNotContainKeys(node1, node2, node3, node4);

    pingCompleted(pingedNodes.get(0));
    assertThat(pingResults).containsKey(node1);
    assertThat(pingResults).doesNotContainKeys(node2, node3, node4);

    pingCompleted(pingedNodes.get(1));
    assertThat(pingResults).containsKey(node2);
    assertThat(pingResults).doesNotContainKeys(node3, node4);

    pingCompleted(node2);
    assertThat(pingResults).containsKey(node3);
    assertThat(pingResults).doesNotContainKeys(node4);

    pingCompleted(node1);
    assertThat(pingResults).containsKey(node4);
  }

  @Test
  void shouldNotQueueANodeMultipleTimes() {
    final NodeRecord node1 = createNewNodeRecord();
    final NodeRecord node2 = createNewNodeRecord();
    final NodeRecord node3 = createNewNodeRecord();
    final NodeRecord node4 = createNewNodeRecord();
    livenessChecker.checkLiveness(node1);
    livenessChecker.checkLiveness(node2);
    livenessChecker.checkLiveness(node3);
    livenessChecker.checkLiveness(node1);

    livenessChecker.checkLiveness(node4);

    verify(pinger).ping(node1);
    verify(pinger).ping(node2);
    verify(pinger).ping(node3);
    verifyNoMoreInteractions(pinger);

    // Duplicated while queued
    livenessChecker.checkLiveness(node4);

    pingCompleted(node1);
    verify(pinger).ping(node4);
    pingCompleted(node2);
    pingCompleted(node3);
    pingCompleted(node4);

    // Doesn't ping node 1 or 4 more than once
    verify(pinger, atMostOnce()).ping(node1);
    verify(pinger, atMostOnce()).ping(node4);
  }

  @Test
  void shouldLimitSizeOfQueue() {
    sendMaxConcurrentPings();

    for (int i = 0; i < LivenessChecker.MAX_QUEUE_SIZE; i++) {
      livenessChecker.checkLiveness(createNewNodeRecord());
    }

    // Will be ignored because the queue is already full
    final NodeRecord ignoredNode = createNewNodeRecord();
    livenessChecker.checkLiveness(ignoredNode);

    // Complete all the active and queued pings
    while (!pingResults.isEmpty()) {
      pingCompleted(pingResults.keySet().iterator().next());
    }

    // Ignored node never made it into the list of things to ping
    verify(pinger, never()).ping(ignoredNode);
  }

  private void pingCompleted(final NodeRecord node) {
    pingResults.get(node).complete(null);
    pingResults.remove(node);
  }

  private List<NodeRecord> sendMaxConcurrentPings() {
    final List<NodeRecord> initialPings = new ArrayList<>();
    for (int i = 0; i < LivenessChecker.MAX_CONCURRENT_PINGS; i++) {
      final NodeRecord node = createNewNodeRecord();
      initialPings.add(node);
      livenessChecker.checkLiveness(node);
    }
    return initialPings;
  }

  private NodeRecord createNewNodeRecord() {
    lastNodeId++;
    return SimpleIdentitySchemaInterpreter.createNodeRecord(lastNodeId);
  }
}
