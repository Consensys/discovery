/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.message.handler;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodesHandlerTest {

  private static final Bytes PEER_ID = Bytes.fromHexString("0x1234567890ABCDEF");
  private static final Bytes REQUEST_ID = Bytes.fromHexString("0x1234");
  private final NodeSession session = mock(NodeSession.class);
  private final NodeTable nodeTable = mock(NodeTable.class);
  private final FindNodeResponseHandler responseHandler = mock(FindNodeResponseHandler.class);
  private final Request<Void> request =
      new Request<>(
          new CompletableFuture<>(),
          id -> new FindNodeMessage(id, singletonList(1)),
          responseHandler);
  private final RequestInfo requestInfo = RequestInfo.create(REQUEST_ID, request);

  private final NodesHandler handler = new NodesHandler();

  @BeforeEach
  public void setUp() {
    when(session.getNodeTable()).thenReturn(nodeTable);
    when(session.getNodeId()).thenReturn(PEER_ID);

    when(session.getRequestInfo(REQUEST_ID)).thenReturn(Optional.of(requestInfo));
  }

  @Test
  void shouldFailWhenRequestIsUnknown() {
    when(session.getRequestInfo(REQUEST_ID)).thenReturn(Optional.empty());

    final NodesMessage message = new NodesMessage(REQUEST_ID, 0, emptyList());
    Assertions.assertThatThrownBy(() -> handler.handle(message, session))
        .hasMessageContaining("not found in session");
  }

  @Test
  void shouldClearRequestWhenResponseHandlerIndicatesResponseComplete() {
    final List<NodeRecord> foundNodes =
        List.of(SimpleIdentitySchemaInterpreter.createNodeRecord(2));
    final NodesMessage message = new NodesMessage(REQUEST_ID, 0, emptyList());
    when(responseHandler.handleResponseMessage(message, session)).thenReturn(true);
    when(responseHandler.getFoundNodes()).thenReturn(foundNodes);

    handler.handle(message, session);

    verify(session).clearRequestInfo(REQUEST_ID, foundNodes);
  }

  @Test
  void shouldUpdateRequestStatusWhenResponseHandlerIndicatesResponseIncomplete() {
    final List<NodeRecord> foundNodes =
        List.of(SimpleIdentitySchemaInterpreter.createNodeRecord(2));
    final NodesMessage message = new NodesMessage(REQUEST_ID, 0, emptyList());
    when(responseHandler.handleResponseMessage(message, session)).thenReturn(false);
    when(responseHandler.getFoundNodes()).thenReturn(foundNodes);

    handler.handle(message, session);

    verify(session, never()).clearRequestInfo(any(), any());
    assertThat(requestInfo.getTaskStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
  }
}
