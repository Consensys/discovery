/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindNodeResponseHandlerTest {
  private static final Bytes PEER_ID = Bytes.fromHexString("0x1234567890ABCDEF");
  private static final Bytes REQUEST_ID = Bytes.fromHexString("0x1234");
  private final NodeSession session = mock(NodeSession.class);
  private final NodeTable nodeTable = mock(NodeTable.class);

  @BeforeEach
  public void setUp() {
    when(session.getNodeTable()).thenReturn(nodeTable);
    when(session.getNodeId()).thenReturn(PEER_ID);
  }

  @Test
  public void shouldAddReceivedRecordsToNodeTableButNotNodeBuckets() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeResponseHandler handler = new FindNodeResponseHandler(singletonList(distance));

    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    assertThat(handler.handleResponseMessage(message, session)).isTrue();

    final NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeInfo.getNodeRecord());
    verify(nodeTable).save(nodeRecordInfo);
    verify(session, never()).putRecordInBucket(nodeRecordInfo);
  }

  @Test
  public void shouldUpdateNodeRecordsWhenSeqNumHigher() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final NodeRecord originalRecord = nodeInfo.getNodeRecord();
    when(nodeTable.getNode(originalRecord.getNodeId()))
        .thenReturn(Optional.of(NodeRecordInfo.createDefault(originalRecord)));
    final int distance = Functions.logDistance(PEER_ID, originalRecord.getNodeId());
    final FindNodeResponseHandler handler = new FindNodeResponseHandler(singletonList(distance));

    final NodeRecord updatedRecord =
        originalRecord.withUpdatedCustomField(
            "test", Bytes.fromHexString("0x8888"), nodeInfo.getPrivateKey());
    final List<NodeRecord> records = singletonList(updatedRecord);
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    assertThat(handler.handleResponseMessage(message, session)).isTrue();

    final NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(updatedRecord);
    verify(nodeTable).save(nodeRecordInfo);
    verify(session, never()).updateNodeRecord(any());
    verify(session, never()).putRecordInBucket(any());
  }

  @Test
  public void shouldUpdateNodeRecordInSessionWhenSelfEnrReturnedWithHigherSeqNum() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final NodeRecord originalRecord = nodeInfo.getNodeRecord();
    when(nodeTable.getNode(originalRecord.getNodeId()))
        .thenReturn(Optional.of(NodeRecordInfo.createDefault(originalRecord)));
    final FindNodeResponseHandler handler = new FindNodeResponseHandler(singletonList(0));
    // Requesting from the node itself
    when(session.getNodeId()).thenReturn(originalRecord.getNodeId());

    final NodeRecord updatedRecord =
        originalRecord.withUpdatedCustomField(
            "test", Bytes.fromHexString("0x8888"), nodeInfo.getPrivateKey());
    final List<NodeRecord> records = singletonList(updatedRecord);
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handleResponseMessage(message, session);

    final NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(updatedRecord);
    verify(nodeTable).save(nodeRecordInfo);
    verify(session).updateNodeRecord(updatedRecord);
    verify(session, never()).putRecordInBucket(any());
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreInvalid() {
    final NodeInfo nodeInfo = TestUtil.generateInvalidNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeResponseHandler handler = new FindNodeResponseHandler(singletonList(distance));
    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handleResponseMessage(message, session);

    verifyNoInteractions(nodeTable);
  }

  @Test
  public void shouldRejectReceivedRecordsThatAreNotAtCorrectDistance() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final int distance = Functions.logDistance(PEER_ID, nodeInfo.getNodeRecord().getNodeId());
    final FindNodeResponseHandler handler =
        new FindNodeResponseHandler(singletonList(distance + 1));
    final List<NodeRecord> records = singletonList(nodeInfo.getNodeRecord());
    final NodesMessage message = new NodesMessage(REQUEST_ID, records.size(), records);
    handler.handleResponseMessage(message, session);

    verifyNoInteractions(nodeTable);
  }
}
