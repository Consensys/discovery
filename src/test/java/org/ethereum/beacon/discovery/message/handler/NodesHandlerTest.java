/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeRequestInfo;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NodesHandlerTest {

  private static final Bytes PEER_ID = Bytes.fromHexString("0x1234567890ABCDEF");
  private static final Bytes REQUEST_ID = Bytes.fromHexString("0x1234");
  private final NodeSession session = mock(NodeSession.class);
  private final NodeTable nodeTable = Mockito.mock(NodeTable.class);
  private final NodesHandler handler = new NodesHandler();

  @BeforeEach
  public void setUp() {
    when(session.getNodeTable()).thenReturn(nodeTable);
    when(session.getNodeId()).thenReturn(PEER_ID);
  }

  @Test
  public void shouldAddReceivedRecordsToNodeTableButNotNodeBuckets() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeRequestInfo requestInfo =
        new FindNodeRequestInfo(
            TaskStatus.SENT, REQUEST_ID, new CompletableFuture<>(), distance, null);
    when(session.getRequestId(REQUEST_ID)).thenReturn(Optional.of(requestInfo));
    final List<NodeRecord> records = Collections.singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message =
        new NodesMessage(REQUEST_ID, records.size(), () -> records, records.size());
    handler.handle(message, session);

    final NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeInfo.getNodeRecord());
    verify(nodeTable).save(nodeRecordInfo);
    verify(session, never()).putRecordInBucket(nodeRecordInfo);
    verify(session).clearRequestId(REQUEST_ID, TaskType.FINDNODE);
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreInvalid() {
    final NodeInfo nodeInfo = TestUtil.generateInvalidNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeRequestInfo requestInfo =
        new FindNodeRequestInfo(
            TaskStatus.SENT, REQUEST_ID, new CompletableFuture<>(), distance, null);
    when(session.getRequestId(REQUEST_ID)).thenReturn(Optional.of(requestInfo));
    final List<NodeRecord> records = Collections.singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message =
        new NodesMessage(REQUEST_ID, records.size(), () -> records, records.size());
    handler.handle(message, session);

    verifyNoInteractions(nodeTable);
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreNotAtCorrectDistance() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeRequestInfo requestInfo =
        new FindNodeRequestInfo(
            TaskStatus.SENT, REQUEST_ID, new CompletableFuture<>(), distance - 1, null);
    when(session.getRequestId(REQUEST_ID)).thenReturn(Optional.of(requestInfo));
    final List<NodeRecord> records = Collections.singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message =
        new NodesMessage(REQUEST_ID, records.size(), () -> records, records.size());
    handler.handle(message, session);

    verifyNoInteractions(nodeTable);
  }
}
