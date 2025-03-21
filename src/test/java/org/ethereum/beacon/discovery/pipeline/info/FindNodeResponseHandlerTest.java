/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.ethereum.beacon.discovery.AddressAccessPolicy.ALLOW_ALL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FindNodeResponseHandlerTest {

  private static final Bytes PEER_ID = Bytes.fromHexString("0x1234567890ABCDEF");
  private static final Bytes REQUEST_ID = Bytes.fromHexString("0x1234");
  public static final AddressAccessPolicy DISALLOW_ALL = record -> false;
  private final NodeSession session = mock(NodeSession.class);

  @BeforeEach
  public void setUp() {
    when(session.getNodeId()).thenReturn(PEER_ID);
  }

  @Test
  public void shouldAddReceivedRecordsToNodeTableButNotNodeBuckets() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeResponseHandler handler =
        new FindNodeResponseHandler(singletonList(distance), ALLOW_ALL);

    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    assertThat(handler.handleResponseMessage(message, session)).isTrue();

    verify(session).onNodeRecordReceived(nodeInfo.getNodeRecord());
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreDisallowed() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeResponseHandler handler =
        new FindNodeResponseHandler(singletonList(distance), DISALLOW_ALL);

    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    assertThat(handler.handleResponseMessage(message, session)).isTrue();

    verify(session, never()).onNodeRecordReceived(nodeInfo.getNodeRecord());
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreInvalid() {
    final NodeInfo nodeInfo = TestUtil.generateInvalidNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeResponseHandler handler =
        new FindNodeResponseHandler(singletonList(distance), ALLOW_ALL);
    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handleResponseMessage(message, session);

    verify(session, never()).onNodeRecordReceived(any());
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreNotAtCorrectDistance() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeResponseHandler handler =
        new FindNodeResponseHandler(singletonList(distance + 1), ALLOW_ALL);
    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handleResponseMessage(message, session);

    verify(session, never()).onNodeRecordReceived(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 17})
  public void shouldRejectInvalidTotalPackets(final int numPackets) {
    final NodesMessage message = new NodesMessage(REQUEST_ID, numPackets, emptyList());
    final FindNodeResponseHandler handler = new FindNodeResponseHandler(emptyList(), ALLOW_ALL);
    assertThatThrownBy(() -> handler.handleResponseMessage(message, session))
        .hasMessageContaining("Invalid number of total packets")
        .isInstanceOf(RuntimeException.class);

    verify(session, never()).onNodeRecordReceived(any());
  }
}
