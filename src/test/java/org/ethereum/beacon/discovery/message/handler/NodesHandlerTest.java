/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodesHandlerTest {

  private static final Bytes PEER_ID = Bytes.fromHexString("0x1234567890ABCDEF");
  private static final Bytes REQUEST_ID = Bytes.fromHexString("0x1234");
  private final NodeSession session = mock(NodeSession.class);
  private final NodesHandler handler = new NodesHandler();

  @BeforeEach
  public void setUp() {
    when(session.getNodeId()).thenReturn(PEER_ID);
  }

  @Test
  public void shouldAddReceivedRecordsToNodeTableButNotNodeBuckets() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    Request<Void> request =
        new Request<>(
            new CompletableFuture<>(),
            id -> new FindNodeMessage(id, singletonList(distance)),
            new FindNodeResponseHandler());
    final RequestInfo requestInfo = RequestInfo.create(REQUEST_ID, request);
    when(session.getRequestInfo(REQUEST_ID)).thenReturn(Optional.of(requestInfo));
    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handle(message, session);

    verify(session).onNodeRecordReceived(nodeInfo.getNodeRecord());
    verify(session).clearRequestInfo(REQUEST_ID, null);
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreInvalid() {
    final NodeInfo nodeInfo = TestUtil.generateInvalidNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    Request<Void> request =
        new Request<>(
            new CompletableFuture<>(),
            id -> new FindNodeMessage(id, singletonList(distance)),
            new FindNodeResponseHandler());
    final RequestInfo requestInfo = RequestInfo.create(REQUEST_ID, request);
    when(session.getRequestInfo(REQUEST_ID)).thenReturn(Optional.of(requestInfo));
    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handle(message, session);

    verify(session, never()).onNodeRecordReceived(any());
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreNotAtCorrectDistance() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    Request<Void> request =
        new Request<>(
            new CompletableFuture<>(),
            id -> new FindNodeMessage(id, singletonList(distance - 1)),
            new FindNodeResponseHandler());
    final RequestInfo requestInfo = RequestInfo.create(REQUEST_ID, request);
    when(session.getRequestInfo(REQUEST_ID)).thenReturn(Optional.of(requestInfo));
    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handle(message, session);

    verify(session, never()).onNodeRecordReceived(any());
  }
}
