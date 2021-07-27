/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.pipeline.handler.NodeSessionManager;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.junit.jupiter.api.Test;

public class NodeSessionTest {
  private final NodeSessionManager nodeSessionManager = mock(NodeSessionManager.class);
  private final Bytes32 nodeId = Bytes32.ZERO;

  @SuppressWarnings("unchecked")
  private final Consumer<NetworkParcel> outgoingPipeline = mock(Consumer.class);

  @SuppressWarnings("unchecked")
  private final ExpirationScheduler<Bytes> expirationScheduler = mock(ExpirationScheduler.class);

  private final KBuckets kBuckets = mock(KBuckets.class);
  private final LocalNodeRecordStore localNodeRecordStore =
      new LocalNodeRecordStore(
          SimpleIdentitySchemaInterpreter.createNodeRecord(Bytes32.fromHexString("0x123456")),
          Bytes.EMPTY,
          mock(NodeRecordListener.class),
          mock(NewAddressHandler.class));

  private final NodeSession session =
      new NodeSession(
          nodeId,
          Optional.empty(),
          InetSocketAddress.createUnresolved("127.0.0.1", 2999),
          nodeSessionManager,
          localNodeRecordStore,
          Bytes32.fromHexString("0x8888"),
          kBuckets,
          outgoingPipeline,
          new Random(1342),
          expirationScheduler);

  @Test
  void onNodeRecordReceived_shouldUpdateRecordWhenNoPreviousValue() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));
    session.onNodeRecordReceived(nodeRecord);
    assertThat(session.getNodeRecord()).contains(nodeRecord);
  }

  @Test
  void onNodeRecordReceived_shouldUpdateRecordWhenSeqNumHigher() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));
    session.onNodeRecordReceived(nodeRecord);
    assertThat(session.getNodeRecord()).contains(nodeRecord);

    final NodeRecord updatedRecord =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x12"), Bytes.EMPTY);
    session.onNodeRecordReceived(updatedRecord);
    assertThat(session.getNodeRecord()).contains(updatedRecord);
  }

  @Test
  void onNodeRecordReceived_shouldNotUpdateRecordWhenSeqNumLower() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));

    final NodeRecord updatedRecord =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x12"), Bytes.EMPTY);
    session.onNodeRecordReceived(updatedRecord);
    assertThat(session.getNodeRecord()).contains(updatedRecord);

    session.onNodeRecordReceived(nodeRecord);
    assertThat(session.getNodeRecord()).contains(updatedRecord);
  }

  @Test
  void onNodeRecordReceived_shouldNotUpdateRecordWhenSeqNumEqual() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));

    final NodeRecord updatedRecord1a =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x12"), Bytes.EMPTY);
    session.onNodeRecordReceived(updatedRecord1a);
    assertThat(session.getNodeRecord()).contains(updatedRecord1a);

    final NodeRecord updatedRecord1b =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x9999"), Bytes.EMPTY);
    session.onNodeRecordReceived(updatedRecord1b);
    assertThat(session.getNodeRecord()).contains(updatedRecord1a);
  }
}
