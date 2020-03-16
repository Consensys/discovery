/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.task.RecursiveLookupTask.FindNodesAction;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecursiveLookupTaskTest {

  private static final NodeRecordFactory NODE_RECORD_FACTORY =
      new NodeRecordFactory(new SimpleIdentitySchemaInterpreter());
  public static final Bytes PEER1_ID =
      Bytes.fromHexString("0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDEEEE");
  public static final Bytes PEER2_ID =
      Bytes.fromHexString("0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDCCCC");
  public static final Bytes PEER3_ID =
      Bytes.fromHexString("0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDFFFF");
  public static final Bytes PEER4_ID =
      Bytes.fromHexString("0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDAAAA");
  public static final Bytes PEER5_ID =
      Bytes.fromHexString("0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD9999");
  public static final NodeRecordInfo PEER1 = createPeer(PEER1_ID);
  public static final NodeRecordInfo PEER2 = createPeer(PEER2_ID);
  public static final NodeRecordInfo PEER3 = createPeer(PEER3_ID);
  public static final NodeRecordInfo PEER4 = createPeer(PEER4_ID);
  public static final NodeRecordInfo PEER5 = createPeer(PEER5_ID);

  private final Bytes TARGET =
      Bytes.fromHexString("0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
  private final NodeTable nodeTable = mock(NodeTable.class);
  private final FindNodesAction findNodesAction = mock(FindNodesAction.class);

  private final Map<NodeRecordInfo, CompletableFuture<Void>> findNodeRequests = new HashMap<>();

  private final RecursiveLookupTask task =
      new RecursiveLookupTask(nodeTable, findNodesAction, 4, TARGET);

  @BeforeEach
  public void setUp() {
    when(findNodesAction.findNodes(any(), anyInt()))
        .then(
            invocation -> {
              final NodeRecordInfo queriedPeer = invocation.getArgument(0);
              final CompletableFuture<Void> result = new CompletableFuture<>();
              findNodeRequests.put(queriedPeer, result);
              return result;
            });
  }

  @Test
  public void shouldQueryThreeClosestNodesToTarget() {
    when(nodeTable.streamClosestNodes(TARGET, 0)).thenReturn(Stream.of(PEER1, PEER2, PEER3, PEER4));

    task.execute();

    verify(findNodesAction).findNodes(PEER1, Functions.logDistance(TARGET, PEER1_ID));
    verify(findNodesAction).findNodes(PEER2, Functions.logDistance(TARGET, PEER2_ID));
    verify(findNodesAction).findNodes(PEER3, Functions.logDistance(TARGET, PEER3_ID));
    verifyNoMoreInteractions(findNodesAction);
  }

  @Test
  public void shouldNotQueryNodesThatAreNotActive() {
    final NodeRecordInfo nonActivePeer = createPeer(PEER1_ID, NodeStatus.SLEEP);
    when(nodeTable.streamClosestNodes(TARGET, 0))
        .thenReturn(Stream.of(nonActivePeer, PEER2, PEER3, PEER4));

    task.execute();

    // Skips PEER1_ID because it's non-active
    verify(findNodesAction).findNodes(PEER2, Functions.logDistance(TARGET, PEER2_ID));
    verify(findNodesAction).findNodes(PEER3, Functions.logDistance(TARGET, PEER3_ID));
    verify(findNodesAction).findNodes(PEER4, Functions.logDistance(TARGET, PEER4_ID));
    verifyNoMoreInteractions(findNodesAction);
  }

  @Test
  public void shouldNotQueryNodesThatHaveNotCheckedLivenessRecently() {
    final NodeRecordInfo nonActivePeer =
        createPeer(
            PEER1_ID, Functions.getTime() - DiscoveryTaskManager.STATUS_EXPIRATION_SECONDS - 1);
    when(nodeTable.streamClosestNodes(TARGET, 0))
        .thenReturn(Stream.of(nonActivePeer, PEER2, PEER3, PEER4));

    task.execute();

    // Skips PEER1_ID because it's non-active
    verify(findNodesAction).findNodes(PEER2, Functions.logDistance(TARGET, PEER2_ID));
    verify(findNodesAction).findNodes(PEER3, Functions.logDistance(TARGET, PEER3_ID));
    verify(findNodesAction).findNodes(PEER4, Functions.logDistance(TARGET, PEER4_ID));
    verifyNoMoreInteractions(findNodesAction);
  }

  @Test
  public void shouldQueryNextClosestPeerWhenRequestCompletes() {
    // thenAnswer so a fresh stream is returned on each invocation
    when(nodeTable.streamClosestNodes(TARGET, 0))
        .thenAnswer(invocation -> Stream.of(PEER1, PEER2, PEER3, PEER4));

    final CompletableFuture<Void> complete = task.execute();

    verify(findNodesAction).findNodes(PEER1, Functions.logDistance(TARGET, PEER1_ID));
    verify(findNodesAction).findNodes(PEER2, Functions.logDistance(TARGET, PEER2_ID));
    verify(findNodesAction).findNodes(PEER3, Functions.logDistance(TARGET, PEER3_ID));
    verifyNoMoreInteractions(findNodesAction);
    assertFalse(complete.isDone());

    // Request to first peer completes.
    findNodeRequests.get(PEER1).complete(null);

    // We should now query the next closest peer we haven't already queried (peer4).
    verify(findNodesAction).findNodes(PEER4, Functions.logDistance(TARGET, PEER4_ID));
    verifyNoMoreInteractions(findNodesAction);
    assertFalse(complete.isDone());

    // Complete remaining requests
    findNodeRequests.get(PEER2).complete(null);
    findNodeRequests.get(PEER3).complete(null);
    findNodeRequests.get(PEER4).complete(null);

    verifyNoMoreInteractions(findNodesAction);
    // Should now be done because all nodes have been queried
    assertTrue(complete.isDone());
  }

  @Test
  public void shouldStopWhenTargetNodeIsFound() {
    final NodeRecordInfo targetPeer = createPeer(TARGET);
    when(nodeTable.streamClosestNodes(TARGET, 0)).thenReturn(Stream.of(PEER1, PEER2, PEER3, PEER4));

    final CompletableFuture<Void> complete = task.execute();

    verify(findNodesAction).findNodes(PEER1, Functions.logDistance(TARGET, PEER1_ID));
    verify(findNodesAction).findNodes(PEER2, Functions.logDistance(TARGET, PEER2_ID));
    verify(findNodesAction).findNodes(PEER3, Functions.logDistance(TARGET, PEER3_ID));
    verifyNoMoreInteractions(findNodesAction);
    assertFalse(complete.isDone());

    // Request to first peer completes. Target peer has now been found.
    when(nodeTable.getNode(TARGET)).thenReturn(Optional.of(targetPeer));
    findNodeRequests.get(PEER1).complete(null);

    // No more requests are made
    verifyNoMoreInteractions(findNodesAction);
    assertTrue(complete.isDone());

    // Complete remaining requests
    findNodeRequests.get(PEER2).complete(null);
    findNodeRequests.get(PEER3).complete(null);

    verifyNoMoreInteractions(findNodesAction);
  }

  @Test
  public void shouldStopWhenTotalQueryLimitIsReached() {
    when(nodeTable.streamClosestNodes(TARGET, 0))
        .thenAnswer(invocation -> Stream.of(PEER1, PEER2, PEER3, PEER4, PEER5));

    final CompletableFuture<Void> complete = task.execute();

    verify(findNodesAction).findNodes(PEER1, Functions.logDistance(TARGET, PEER1_ID));
    verify(findNodesAction).findNodes(PEER2, Functions.logDistance(TARGET, PEER2_ID));
    verify(findNodesAction).findNodes(PEER3, Functions.logDistance(TARGET, PEER3_ID));
    verifyNoMoreInteractions(findNodesAction);
    assertFalse(complete.isDone());

    // Requests complete
    findNodeRequests.get(PEER1).complete(null);
    findNodeRequests.get(PEER2).complete(null);
    findNodeRequests.get(PEER3).complete(null);

    // There are two peers remaining but only 1 request before we hit the total request limit
    verify(findNodesAction).findNodes(PEER4, Functions.logDistance(TARGET, PEER4_ID));
    verifyNoMoreInteractions(findNodesAction);
    assertFalse(complete.isDone());

    // And when that last request completes, we're done.
    findNodeRequests.get(PEER4).complete(null);
    verifyNoMoreInteractions(findNodesAction);
    assertTrue(complete.isDone());
  }

  private static NodeRecordInfo createPeer(final Bytes nodeId) {
    return createPeer(nodeId, NodeStatus.ACTIVE);
  }

  private static NodeRecordInfo createPeer(final Bytes nodeId, final long lastRetry) {
    return createPeer(nodeId, NodeStatus.ACTIVE, lastRetry);
  }

  private static NodeRecordInfo createPeer(final Bytes nodeId, final NodeStatus status) {
    return createPeer(nodeId, status, Functions.getTime() + 100000000L);
  }

  private static NodeRecordInfo createPeer(
      final Bytes nodeId, final NodeStatus status, final long lastRetry) {
    return new NodeRecordInfo(
        NODE_RECORD_FACTORY.createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.PKEY_SECP256K1, nodeId)),
        lastRetry, // Long way in the future
        status,
        0);
  }
}
