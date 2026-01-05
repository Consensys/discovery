/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static java.util.Collections.singletonList;
import static org.ethereum.beacon.discovery.AddressAccessPolicy.ALLOW_ALL;
import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.ethereum.beacon.discovery.pipeline.Field.BAD_PACKET;
import static org.ethereum.beacon.discovery.pipeline.Field.MASKING_IV;
import static org.ethereum.beacon.discovery.pipeline.Field.MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_HANDSHAKE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.SESSION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.crypto.InMemorySecretKeyHolder;
import org.ethereum.beacon.discovery.crypto.SecretKeyHolder;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.handler.EnrUpdateTracker;
import org.ethereum.beacon.discovery.message.handler.ExternalAddressSelector;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.PipelineImpl;
import org.ethereum.beacon.discovery.pipeline.handler.HandshakeMessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessageHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NodeSessionManager;
import org.ethereum.beacon.discovery.pipeline.handler.WhoAreYouPacketHandler;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.MultiPacketResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"DoubleBraceInitialization"})
public class HandshakeHandlersTest {
  private final Random rnd = new Random(1);
  private final Clock clock = Clock.systemUTC();

  @Test
  public void authHandlerWithMessageRoundTripTest() throws Exception {
    // Node1
    NodeInfo nodePair1 = TestUtil.generateUnverifiedNode(30303);
    NodeRecord nodeRecord1 = nodePair1.getNodeRecord();
    SecretKeyHolder secretKeyHolder1 = new InMemorySecretKeyHolder(nodePair1.getSecretKey());
    // Node2
    NodeInfo nodePair2 = TestUtil.generateUnverifiedNode(30304);
    NodeRecord nodeRecord2 = nodePair2.getNodeRecord();
    SecretKeyHolder secretKeyHolder2 = new InMemorySecretKeyHolder(nodePair2.getSecretKey());

    final LocalNodeRecordStore localNodeRecordStoreAt1 =
        new LocalNodeRecordStore(
            nodeRecord1, secretKeyHolder1, NodeRecordListener.NOOP, NewAddressHandler.NOOP);
    KBuckets nodeBucketStorage1 =
        new KBuckets(clock, localNodeRecordStoreAt1, new LivenessChecker(clock));
    KBuckets nodeBucketStorage2 =
        new KBuckets(
            clock,
            new LocalNodeRecordStore(
                nodeRecord2, secretKeyHolder2, NodeRecordListener.NOOP, NewAddressHandler.NOOP),
            new LivenessChecker(clock));

    // Node1 create AuthHeaderPacket
    LinkedBlockingQueue<RawPacket> outgoing1Packets = new LinkedBlockingQueue<>();
    final Consumer<NetworkParcel> outgoingMessages1to2 =
        parcel -> {
          System.out.println("Outgoing packet from 1 to 2: " + parcel.getPacket());
          outgoing1Packets.add(parcel.getPacket());
        };
    final ExpirationSchedulerFactory expirationSchedulerFactory =
        new ExpirationSchedulerFactory(Executors.newSingleThreadScheduledExecutor());
    final ExpirationScheduler<Bytes> reqeustExpirationScheduler =
        expirationSchedulerFactory.create(60, TimeUnit.SECONDS);
    NodeSession nodeSessionAt1For2 =
        new NodeSession(
            nodeRecord2.getNodeId(),
            Optional.of(nodeRecord2),
            nodePair2.getNodeRecord().getUdpAddress().orElseThrow(),
            mock(NodeSessionManager.class),
            localNodeRecordStoreAt1,
          secretKeyHolder1,
            nodeBucketStorage1,
            outgoingMessages1to2,
            rnd,
            reqeustExpirationScheduler);
    LinkedBlockingQueue<RawPacket> outgoing2Packets = new LinkedBlockingQueue<>();
    final Consumer<NetworkParcel> outgoingMessages2to1 =
        parcel -> {
          // do nothing, we don't need to test it here
          System.out.println("Outgoing packet from 2 to 1: " + parcel.getPacket());
          outgoing2Packets.add(parcel.getPacket());
        };
    NodeSession nodeSessionAt2For1 =
        new NodeSession(
            nodeRecord1.getNodeId(),
            Optional.of(nodeRecord1),
            nodeRecord1.getUdpAddress().orElseThrow(),
            mock(NodeSessionManager.class),
            new LocalNodeRecordStore(
                nodeRecord2, secretKeyHolder2, NodeRecordListener.NOOP, NewAddressHandler.NOOP),
          secretKeyHolder2,
            nodeBucketStorage2,
            outgoingMessages2to1,
            rnd,
            reqeustExpirationScheduler);

    Scheduler taskScheduler = Schedulers.createDefault().events();
    Pipeline outgoingPipeline = new PipelineImpl().build();
    WhoAreYouPacketHandler whoAreYouPacketHandlerNode1 =
        new WhoAreYouPacketHandler(outgoingPipeline, taskScheduler);

    nodeSessionAt1For2.sendOutgoingRandom(Bytes.random(128));
    Bytes12 randomMessageNonce = nodeSessionAt1For2.getLastOutboundNonce().orElseThrow();
    outgoing1Packets.clear();

    Envelope envelopeAt1From2 = new Envelope();
    Bytes16 idNonce = Bytes16.random(rnd);
    WhoAreYouPacket whoAreYouPacket =
        WhoAreYouPacket.create(
            Header.createWhoAreYouHeader(randomMessageNonce, idNonce, UInt64.ZERO));
    nodeSessionAt2For1.sendOutgoingWhoAreYou(whoAreYouPacket);

    envelopeAt1From2.put(Field.PACKET_WHOAREYOU, whoAreYouPacket);
    envelopeAt1From2.put(Field.SESSION, nodeSessionAt1For2);
    Request<Void> request =
        new Request<>(
            new CompletableFuture<>(),
            id -> new FindNodeMessage(id, singletonList(1)),
            new FindNodeResponseHandler(singletonList(1), ALLOW_ALL));
    nodeSessionAt1For2.createNextRequest(request);

    RawPacket whoAreYouRawPacket = outgoing2Packets.poll(1, TimeUnit.SECONDS);
    envelopeAt1From2.put(MASKING_IV, whoAreYouRawPacket.getMaskingIV());
    whoAreYouPacketHandlerNode1.handle(envelopeAt1From2);

    // Node2 handle AuthHeaderPacket and finish handshake
    HandshakeMessagePacketHandler handshakeMessagePacketHandlerNode2 =
        new HandshakeMessagePacketHandler(
            outgoingPipeline,
            taskScheduler,
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            mock(NodeSessionManager.class),
            ALLOW_ALL);
    Envelope envelopeAt2From1 = new Envelope();
    RawPacket handshakeRawPacket = outgoing1Packets.poll(1, TimeUnit.SECONDS);
    envelopeAt2From1.put(
        PACKET_HANDSHAKE,
        (HandshakeMessagePacket)
            handshakeRawPacket.demaskPacket(nodePair2.getNodeRecord().getNodeId()));
    envelopeAt2From1.put(SESSION, nodeSessionAt2For1);
    assertFalse(nodeSessionAt2For1.isAuthenticated());

    envelopeAt2From1.put(MASKING_IV, handshakeRawPacket.getMaskingIV());
    handshakeMessagePacketHandlerNode2.handle(envelopeAt2From1);
    assertTrue(nodeSessionAt2For1.isAuthenticated());

    // Node 1 handles message from Node 2
    MessagePacketHandler messagePacketHandler1 =
        new MessagePacketHandler(NodeRecordFactory.DEFAULT);
    Envelope envelopeAt1From2WithMessage = new Envelope();
    Bytes12 pingAuthTag = nodeSessionAt1For2.generateNonce();
    Bytes16 maskingIV = nodeSessionAt1For2.generateMaskingIV();
    OrdinaryMessagePacket pingPacketFrom2To1 =
        createPingPacket(
            maskingIV,
            pingAuthTag,
            nodeSessionAt2For1,
            nodeSessionAt2For1
                .createNextRequest(
                    new Request<Void>(
                        new CompletableFuture<>(),
                        id -> new PingMessage(id, UInt64.ZERO),
                        MultiPacketResponseHandler.SINGLE_PACKET_RESPONSE_HANDLER))
                .getRequestId());
    envelopeAt1From2WithMessage.put(PACKET_MESSAGE, pingPacketFrom2To1);
    envelopeAt1From2WithMessage.put(SESSION, nodeSessionAt1For2);
    envelopeAt1From2WithMessage.put(MASKING_IV, maskingIV);
    messagePacketHandler1.handle(envelopeAt1From2WithMessage);
    assertNull(envelopeAt1From2WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt1From2WithMessage.get(MESSAGE));

    MessageHandler messageHandler =
        new MessageHandler(
            localNodeRecordStoreAt1,
            TalkHandler.NOOP,
            EnrUpdateTracker.EnrUpdater.NOOP,
            ExternalAddressSelector.NOOP);
    messageHandler.handle(envelopeAt1From2WithMessage);

    // Node 2 handles message from Node 1
    MessagePacketHandler messagePacketHandler2 =
        new MessagePacketHandler(NodeRecordFactory.DEFAULT);
    Envelope envelopeAt2From1WithMessage = new Envelope();
    RawPacket rawMesssagePacket = outgoing1Packets.poll(1, TimeUnit.SECONDS);
    Packet<?> pongPacketFrom1To2 =
        rawMesssagePacket.demaskPacket(nodePair2.getNodeRecord().getNodeId());
    envelopeAt2From1WithMessage.put(PACKET_MESSAGE, (MessagePacket<?>) pongPacketFrom1To2);
    envelopeAt2From1WithMessage.put(SESSION, nodeSessionAt2For1);
    envelopeAt2From1WithMessage.put(MASKING_IV, rawMesssagePacket.getMaskingIV());
    messagePacketHandler2.handle(envelopeAt2From1WithMessage);
    assertNull(envelopeAt2From1WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt2From1WithMessage.get(MESSAGE));
  }

  private OrdinaryMessagePacket createPingPacket(
      Bytes16 maskingIV, Bytes12 authTag, NodeSession session, Bytes requestId) {

    PingMessage pingMessage = createPing(session, requestId);
    Header<OrdinaryAuthData> header = Header.createOrdinaryHeader(session.getHomeNodeId(), authTag);
    return OrdinaryMessagePacket.create(maskingIV, header, pingMessage, session.getInitiatorKey());
  }

  private static PingMessage createPing(NodeSession session, Bytes requestId) {
    return new PingMessage(requestId, session.getNodeRecord().orElseThrow().getSeq());
  }
}
