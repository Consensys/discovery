/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.crypto.DefaultSigner;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NodeSessionManagerTest {

  private static final SecretKey STATIC_NODE_SECRET = Functions.randomKeyPair().secretKey();
  public static final Bytes NODE_ID = Bytes.fromHexString("0x888888");
  private final NodeInfo homeNodeInfo = TestUtil.generateNode(9000);
  private final NodeRecord homeNodeRecord = homeNodeInfo.getNodeRecord();
  private final KBuckets nodeBucketStorage = mock(KBuckets.class);
  private final ExpirationSchedulerFactory expirationSchedulerFactory =
      new ExpirationSchedulerFactory(Executors.newSingleThreadScheduledExecutor());
  private final Pipeline outgoingPipeline = mock(Pipeline.class);

  private final NodeSessionManager handler =
      new NodeSessionManager(
          new LocalNodeRecordStore(
              homeNodeRecord,
              new DefaultSigner(homeNodeInfo.getSecretKey()),
              NodeRecordListener.NOOP,
              NewAddressHandler.NOOP),
          new DefaultSigner(STATIC_NODE_SECRET),
          nodeBucketStorage,
          outgoingPipeline,
          expirationSchedulerFactory,
          false);

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
  public void shouldDialPeerOverIpv6WhenIpv6BindAvailableEvenIfHomeRecordIsIpv4Only()
      throws UnknownHostException {
    final NodeSessionManager ipv6BindAvailableHandler =
        new NodeSessionManager(
            new LocalNodeRecordStore(
                homeNodeRecord,
                new DefaultSigner(homeNodeInfo.getSecretKey()),
                NodeRecordListener.NOOP,
                NewAddressHandler.NOOP),
            new DefaultSigner(STATIC_NODE_SECRET),
            nodeBucketStorage,
            outgoingPipeline,
            expirationSchedulerFactory,
            true);

    final InetSocketAddress peerIpv4 = new InetSocketAddress("192.0.2.1", 30303);
    final InetSocketAddress peerIpv6 =
        new InetSocketAddress(Inet6Address.getByName("2001:db8::1"), 30304);
    final NodeRecord dualStackPeer = createDualStackPeerRecord(peerIpv4, peerIpv6);

    final NodeSession session =
        lookupSessionForOutgoingMessage(dualStackPeer, ipv6BindAvailableHandler);

    assertThat(session).isNotNull();
    assertThat(session.getRemoteAddress().getAddress()).isInstanceOf(Inet6Address.class);
    assertThat(session.getRemoteAddress()).isEqualTo(peerIpv6);
  }

  @Test
  public void shouldDialPeerOverIpv4WhenIpv6BindNotAvailableAndHomeRecordIsIpv4Only()
      throws UnknownHostException {
    final InetSocketAddress peerIpv4 = new InetSocketAddress("192.0.2.1", 30303);
    final InetSocketAddress peerIpv6 =
        new InetSocketAddress(Inet6Address.getByName("2001:db8::1"), 30304);
    final NodeRecord dualStackPeer = createDualStackPeerRecord(peerIpv4, peerIpv6);

    final NodeSession session = lookupSessionForOutgoingMessage(dualStackPeer, handler);

    assertThat(session).isNotNull();
    assertThat(session.getRemoteAddress()).isEqualTo(peerIpv4);
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

  private NodeSession lookupSessionForOutgoingMessage(
      final NodeRecord peerRecord, final NodeSessionManager handlerToUse) {
    final Envelope envelope = new Envelope();
    envelope.put(Field.SESSION_LOOKUP, new SessionLookup(NODE_ID));
    envelope.put(Field.NODE, peerRecord);
    handlerToUse.handle(envelope);

    return envelope.get(Field.SESSION);
  }

  private NodeRecord createDualStackPeerRecord(
      final InetSocketAddress ipv4, final InetSocketAddress ipv6) {
    return SimpleIdentitySchemaInterpreter.createNodeRecord(
        NODE_ID,
        new EnrField(EnrField.IP_V4, Bytes.wrap(ipv4.getAddress().getAddress())),
        new EnrField(EnrField.UDP, ipv4.getPort()),
        new EnrField(EnrField.IP_V6, Bytes.wrap(ipv6.getAddress().getAddress())),
        new EnrField(EnrField.UDP_V6, ipv6.getPort()));
  }
}
