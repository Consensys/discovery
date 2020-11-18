/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.storage.NonceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NodeSessionManagerTest {

  private static final Bytes STATIC_NODE_KEY = Bytes.fromHexString("0x1234");
  public static final Bytes NODE_ID = Bytes.fromHexString("0x888888");
  private final NodeInfo homeNodeInfo = TestUtil.generateNode(9000);
  private final NodeRecord homeNodeRecord = homeNodeInfo.getNodeRecord();
  private final NodeBucketStorage nodeBucketStorage = mock(NodeBucketStorage.class);
  private final NonceRepository nonceRepository = mock(NonceRepository.class);
  private final ExpirationSchedulerFactory expirationSchedulerFactory =
      new ExpirationSchedulerFactory(Executors.newSingleThreadScheduledExecutor());
  private final NodeTable nodeTable = mock(NodeTable.class);
  private final Pipeline outgoingPipeline = mock(Pipeline.class);

  private final NodeSessionManager handler =
      new NodeSessionManager(
          new LocalNodeRecordStore(
              homeNodeRecord, homeNodeInfo.getPrivateKey(), NodeRecordListener.NOOP),
          STATIC_NODE_KEY,
          nodeBucketStorage,
          nonceRepository,
          nodeTable,
          outgoingPipeline,
          expirationSchedulerFactory);

  @AfterEach
  public void tearDown() {
    expirationSchedulerFactory.stop();
  }

  @Test
  public void shouldGetSameSessionForIncomingMessagesWithSameIdAndSender() {
    final NodeSession session =
        lookupSessionForIncomingMessage(NODE_ID, new InetSocketAddress(9000));
    assertThat(lookupSessionForIncomingMessage(NODE_ID, new InetSocketAddress(9000)))
        .isSameAs(session);
  }

  @Test
  public void shouldGetSameSessionForIncomingAndOutgoingMessagesWithSameIdAndSender() {
    final NodeSession incoming =
        lookupSessionForIncomingMessage(NODE_ID, new InetSocketAddress(9000));
    assertThat(lookupSessionForOutgoingMessage(new InetSocketAddress(9000))).isSameAs(incoming);
  }

  @Test
  public void shouldGetSameSessionForOutgoingMessagesWithSameIdAndSender() {
    final NodeSession session = lookupSessionForOutgoingMessage(new InetSocketAddress(9000));
    assertThat(lookupSessionForOutgoingMessage(new InetSocketAddress(9000))).isSameAs(session);
  }

  @Test
  public void shouldGetDifferentSessionWhenRemoteSenderChanges() {
    final NodeSession session =
        lookupSessionForIncomingMessage(NODE_ID, new InetSocketAddress(9000));
    assertThat(session.getNodeId()).isEqualTo(NODE_ID);

    final NodeSession session2 =
        lookupSessionForIncomingMessage(NODE_ID, new InetSocketAddress(9001));
    assertThat(session).isNotSameAs(session2);
  }

  @Test
  public void shouldGetDifferentSessionWhenOutgoingDestinationChanges() {
    final NodeSession session = lookupSessionForOutgoingMessage(new InetSocketAddress(9000));
    assertThat(lookupSessionForOutgoingMessage(new InetSocketAddress(9001))).isNotSameAs(session);
  }

  @Test
  public void shouldGetDifferentSessionWhenIncomingAndOutgoingRemoteAddressesDiffer() {
    final NodeSession session =
        lookupSessionForIncomingMessage(NODE_ID, new InetSocketAddress(9000));
    assertThat(lookupSessionForOutgoingMessage(new InetSocketAddress(9001))).isNotSameAs(session);
  }

  @Test
  public void shouldGetDifferentSessionWhenNodeIdDiffers() {
    final InetSocketAddress remoteSender = new InetSocketAddress(9000);
    final NodeSession session1 = lookupSessionForIncomingMessage(NODE_ID, remoteSender);
    final NodeSession session2 =
        lookupSessionForIncomingMessage(Bytes.fromHexString("0x9999"), remoteSender);
    assertThat(session1).isNotSameAs(session2);
  }

  @Test
  void shouldNotGetASessionWhenNoAddressIsAvailable() {
    final NodeRecord nodeRecord =
        new NodeRecordFactory(new SimpleIdentitySchemaInterpreter())
            .createFromValues(
                UInt64.ONE,
                new EnrField(EnrField.ID, IdentitySchema.V4),
                new EnrField(EnrField.PKEY_SECP256K1, NODE_ID));
    final Envelope envelope = new Envelope();
    envelope.put(Field.SESSION_LOOKUP, new SessionLookup(NODE_ID));
    envelope.put(Field.NODE, nodeRecord);
    handler.handle(envelope);

    assertThat(envelope.contains(Field.SESSION)).isFalse();
  }

  private NodeSession lookupSessionForIncomingMessage(
      final Bytes nodeId, final InetSocketAddress remoteSender) {
    final Envelope envelope = new Envelope();
    envelope.put(Field.SESSION_LOOKUP, new SessionLookup(nodeId));
    envelope.put(Field.REMOTE_SENDER, remoteSender);
    handler.handle(envelope);

    return envelope.get(Field.SESSION);
  }

  private NodeSession lookupSessionForOutgoingMessage(final InetSocketAddress destination) {
    final Envelope envelope = new Envelope();
    envelope.put(Field.SESSION_LOOKUP, new SessionLookup(NODE_ID));
    envelope.put(
        Field.NODE, SimpleIdentitySchemaInterpreter.createNodeRecord(NODE_ID, destination));
    handler.handle(envelope);

    return envelope.get(Field.SESSION);
  }
}
