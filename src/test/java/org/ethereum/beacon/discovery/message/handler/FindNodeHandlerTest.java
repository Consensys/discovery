/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.message.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.crypto.DefaultSigner;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class FindNodeHandlerTest {

  private int counter = 1000;
  private final NodeInfo homeNodePair = TestUtil.generateUnverifiedNode(30303);
  private final NodeRecord homeNodeRecord = homeNodePair.getNodeRecord();
  private final Clock clock = Clock.systemUTC();
  private final KBuckets nodeBucketStorage =
      new KBuckets(
          Clock.fixed(Instant.ofEpochSecond(100000), ZoneId.of("UTC")),
          new LocalNodeRecordStore(
              homeNodeRecord,
              DefaultSigner.create(Functions.randomKeyPair().secretKey()),
              NodeRecordListener.NOOP,
              NewAddressHandler.NOOP),
          new LivenessChecker(clock));
  private final NodeSession session = mock(NodeSession.class);
  private final Map<Integer, List<NodeRecord>> tableRecords = new HashMap<>();

  private final FindNodeHandler nodeHandler = new FindNodeHandler();

  {
    when(session.getNodeRecordsInBucket(anyInt()))
        .thenAnswer(
            invocation ->
                nodeBucketStorage.getLiveNodeRecords(invocation.getArgument(0, Integer.class)));
    for (int i = 0; i < 256; i++) {
      for (int j = 0; j < i % 3; j++) {
        NodeRecord record = generateNodeAtDistance(i);
        tableRecords.computeIfAbsent(i, __ -> new ArrayList<>()).add(record);
        nodeBucketStorage.onNodeContacted(record);
      }
    }
  }

  @Test
  void testSimplePositive() {
    nodeHandler.handle(
        new FindNodeMessage(Bytes.fromHexString("0xaa00"), List.of(255, 254, 253)), session);

    ArgumentCaptor<V5Message> captor = ArgumentCaptor.forClass(V5Message.class);
    Mockito.verify(session, times(1)).sendOutgoingOrdinary(captor.capture());

    V5Message v5Message = captor.getValue();
    assertThat(v5Message).isInstanceOf(NodesMessage.class);
    NodesMessage msg = (NodesMessage) v5Message;
    assertThat(msg.getRequestId()).isEqualTo(Bytes.fromHexString("0xaa00"));
    assertThat(msg.getTotal()).isEqualTo(1);

    List<NodeRecord> expectedRecords =
        Stream.of(255, 254, 253)
            .flatMap(i -> tableRecords.getOrDefault(i, Collections.emptyList()).stream())
            .collect(Collectors.toList());
    List<NodeRecord> actualRecords = msg.getNodeRecords();
    assertThat(actualRecords.stream().map(NodeRecord::getTcpAddress))
        .containsExactlyInAnyOrderElementsOf(
            expectedRecords.stream().map(NodeRecord::getTcpAddress).collect(Collectors.toList()));
  }

  @Test
  void testMaxResponseSizeRespected() {
    nodeHandler.handle(
        new FindNodeMessage(
            Bytes.fromHexString("0xaa00"),
            IntStream.range(0, 255).boxed().collect(Collectors.toList())),
        session);

    ArgumentCaptor<V5Message> captor = ArgumentCaptor.forClass(V5Message.class);
    Mockito.verify(session, atLeastOnce()).sendOutgoingOrdinary(captor.capture());

    List<V5Message> v5Messages = captor.getAllValues();
    assertThat(v5Messages).allMatch(m -> m instanceof NodesMessage);
    List<NodesMessage> nodesMessages =
        v5Messages.stream().map(m -> (NodesMessage) m).collect(Collectors.toList());
    assertThat(nodesMessages)
        .allSatisfy(
            msg -> {
              assertThat(msg.getRequestId()).isEqualTo(Bytes.fromHexString("0xaa00"));
              assertThat(msg.getTotal()).isGreaterThan(1);
              assertThat(msg.getTotal()).isLessThanOrEqualTo(4);
              assertThat(msg.getTotal()).isEqualTo(v5Messages.size());
            });

    List<InetSocketAddress> actualRecordAddr =
        v5Messages.stream()
            .map(m -> (NodesMessage) m)
            .flatMap(m -> m.getNodeRecords().stream())
            .map(r -> r.getTcpAddress().get())
            .collect(Collectors.toList());

    assertThat(actualRecordAddr).contains(homeNodeRecord.getTcpAddress().get());
    assertThat(actualRecordAddr)
        .containsAll(
            tableRecords.get(1).stream()
                .map(r -> r.getTcpAddress().get())
                .collect(Collectors.toList()));
  }

  @Test
  void testShouldReturnEmptyOnLargeDistances() {
    nodeHandler.handle(
        new FindNodeMessage(
            Bytes.fromHexString("0xaa00"), List.of(Integer.MAX_VALUE, 65536, 65535, 256)),
        session);

    ArgumentCaptor<V5Message> captor = ArgumentCaptor.forClass(V5Message.class);
    Mockito.verify(session, times(1)).sendOutgoingOrdinary(captor.capture());

    V5Message v5Message = captor.getValue();
    assertThat(v5Message).isInstanceOf(NodesMessage.class);
    NodesMessage msg = (NodesMessage) v5Message;
    assertThat(msg.getRequestId()).isEqualTo(Bytes.fromHexString("0xaa00"));
    assertThat(msg.getTotal()).isEqualTo(1);
    assertThat(msg.getNodeRecords()).isEmpty();
  }

  private NodeRecord generateNodeAtDistance(int distance) {
    if (distance == 0) {
      return homeNodeRecord;
    }

    Bytes nodeId = homeNodeRecord.getNodeId();
    int nByte = 31 - (distance - 1) / 8;
    int nBit = (distance - 1) % 8;
    int b = nodeId.get(nByte) & 0xFF;
    int newB = b ^ (1 << nBit);
    Bytes prefix = nodeId.slice(0, nByte);
    Bytes suffix = Bytes.random(31 - nByte);
    Bytes newNodeId = Bytes.wrap(prefix, Bytes.of(newB), suffix);
    NodeInfo nodeInfo = TestUtil.generateNode(counter++);
    NodeRecord record = spy(nodeInfo.getNodeRecord());
    when(record.getNodeId()).thenReturn(newNodeId);
    assertThat(Functions.logDistance(nodeId, newNodeId)).isEqualTo(distance);
    return record;
  }
}
