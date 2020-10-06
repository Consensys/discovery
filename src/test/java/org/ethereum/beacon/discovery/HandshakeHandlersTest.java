/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static java.util.Collections.singletonList;
import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.ethereum.beacon.discovery.pipeline.Field.BAD_PACKET;
import static org.ethereum.beacon.discovery.pipeline.Field.MASKING_IV;
import static org.ethereum.beacon.discovery.pipeline.Field.MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_AUTH_HEADER_MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.SESSION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.PipelineImpl;
import org.ethereum.beacon.discovery.pipeline.handler.HandshakeMessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.IncomingDataPacker;
import org.ethereum.beacon.discovery.pipeline.handler.MessageHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessagePacketHandler;
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
import org.ethereum.beacon.discovery.storage.NonceRepository;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"DoubleBraceInitialization"})
public class HandshakeHandlersTest {

  Random rnd = new Random(1);

  @Test
  @SuppressWarnings("rawtypes")
  public void authHandlerWithMessageRoundTripTest() throws Exception {
    // Node1
    NodeInfo nodePair1 = TestUtil.generateUnverifiedNode(30303);
    NodeRecord nodeRecord1 = nodePair1.getNodeRecord();
    // Node2
    NodeInfo nodePair2 = TestUtil.generateUnverifiedNode(30304);
    NodeRecord nodeRecord2 = nodePair2.getNodeRecord();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database1 = Database.inMemoryDB();
    Database database2 = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage1 =
        nodeTableStorageFactory.createTable(
            database1, TEST_SERIALIZER, (oldSeq) -> nodeRecord1, () -> List.of(nodeRecord2));
    NodeBucketStorage nodeBucketStorage1 =
        nodeTableStorageFactory.createBucketStorage(database1, TEST_SERIALIZER, nodeRecord1);
    NodeTableStorage nodeTableStorage2 =
        nodeTableStorageFactory.createTable(
            database2, TEST_SERIALIZER, (oldSeq) -> nodeRecord2, () -> List.of(nodeRecord1));
    NodeBucketStorage nodeBucketStorage2 =
        nodeTableStorageFactory.createBucketStorage(database2, TEST_SERIALIZER, nodeRecord2);

    // Node1 create AuthHeaderPacket
    LinkedBlockingQueue<Packet<?>> outgoing1Packets = new LinkedBlockingQueue<>();
    final Consumer<NetworkParcel> outgoingMessages1to2 =
        parcel -> {
          System.out.println("Outgoing packet from 1 to 2: " + parcel.getPacket());
          outgoing1Packets.add(
              parcel.getPacket().demaskPacket(nodePair2.getNodeRecord().getNodeId()));
        };
    NonceRepository nonceRepository1 = new NonceRepository();
    final LocalNodeRecordStore localNodeRecordStoreAt1 =
        new LocalNodeRecordStore(nodeRecord1, nodePair1.getPrivateKey(), NodeRecordListener.NOOP);
    final ExpirationSchedulerFactory expirationSchedulerFactory =
        new ExpirationSchedulerFactory(Executors.newSingleThreadScheduledExecutor());
    final ExpirationScheduler<Bytes> reqeustExpirationScheduler =
        expirationSchedulerFactory.create(60, TimeUnit.SECONDS);
    NodeSession nodeSessionAt1For2 =
        new NodeSession(
            nodeRecord2.getNodeId(),
            Optional.of(nodeRecord2),
            nodePair2.getNodeRecord().getUdpAddress().orElseThrow(),
            localNodeRecordStoreAt1,
            nodePair1.getPrivateKey(),
            nodeTableStorage1.get(),
            nodeBucketStorage1,
            nonceRepository1,
            outgoingMessages1to2,
            rnd,
            reqeustExpirationScheduler);
    final Consumer<NetworkParcel> outgoingMessages2to1 =
        packet -> {
          // do nothing, we don't need to test it here
        };
    NodeSession nodeSessionAt2For1 =
        new NodeSession(
            nodeRecord1.getNodeId(),
            Optional.of(nodeRecord1),
            nodeRecord1.getUdpAddress().orElseThrow(),
            new LocalNodeRecordStore(
                nodeRecord2, nodePair2.getPrivateKey(), NodeRecordListener.NOOP),
            nodePair2.getPrivateKey(),
            nodeTableStorage2.get(),
            nodeBucketStorage2,
            new NonceRepository(),
            outgoingMessages2to1,
            rnd,
            reqeustExpirationScheduler);

    Scheduler taskScheduler = Schedulers.createDefault().events();
    Pipeline outgoingPipeline = new PipelineImpl().build();
    IncomingDataPacker incomingDataPacker1 = new IncomingDataPacker(nodeRecord1.getNodeId());
    WhoAreYouPacketHandler whoAreYouPacketHandlerNode1 =
        new WhoAreYouPacketHandler(outgoingPipeline, taskScheduler);
    Envelope envelopeAt1From2 = new Envelope();
    Bytes16 idNonce = Bytes16.random(rnd);
    nodeSessionAt2For1.setIdNonce(idNonce);
    Bytes12 authTag = nodeSessionAt2For1.generateNonce();
    nonceRepository1.put(authTag, nodeSessionAt1For2);
    envelopeAt1From2.put(
        Field.PACKET_WHOAREYOU,
        WhoAreYouPacket.create(
            Header.createWhoAreYouHeader(
                authTag,
                idNonce,
                UInt64.ZERO)));
    envelopeAt1From2.put(Field.SESSION, nodeSessionAt1For2);
    Request<Void> request =
        new Request<>(
            new CompletableFuture<>(),
            id -> new FindNodeMessage(id, singletonList(1)),
            new FindNodeResponseHandler());
    nodeSessionAt1For2.createNextRequest(request);

    envelopeAt1From2.put(MASKING_IV, Bytes16.random(rnd));
    whoAreYouPacketHandlerNode1.handle(envelopeAt1From2);

    // Node2 handle AuthHeaderPacket and finish handshake
    HandshakeMessagePacketHandler handshakeMessagePacketHandlerNode2 =
        new HandshakeMessagePacketHandler(
            outgoingPipeline, taskScheduler, NODE_RECORD_FACTORY_NO_VERIFICATION);
    Envelope envelopeAt2From1 = new Envelope();
    envelopeAt2From1.put(PACKET_AUTH_HEADER_MESSAGE, outgoing1Packets.poll(1, TimeUnit.SECONDS));
    envelopeAt2From1.put(SESSION, nodeSessionAt2For1);
    assertFalse(nodeSessionAt2For1.isAuthenticated());
    handshakeMessagePacketHandlerNode2.handle(envelopeAt2From1);
    assertTrue(nodeSessionAt2For1.isAuthenticated());

    // Node 1 handles message from Node 2
    MessagePacketHandler messagePacketHandler1 =
        new MessagePacketHandler(NodeRecordFactory.DEFAULT);
    Envelope envelopeAt1From2WithMessage = new Envelope();
    Bytes12 pingAuthTag = nodeSessionAt1For2.generateNonce();
    OrdinaryMessagePacket pingPacketFrom2To1 =
        createPingPacket(
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
    messagePacketHandler1.handle(envelopeAt1From2WithMessage);
    assertNull(envelopeAt1From2WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt1From2WithMessage.get(MESSAGE));

    MessageHandler messageHandler = new MessageHandler(localNodeRecordStoreAt1, TalkHandler.NOOP);
    messageHandler.handle(envelopeAt1From2WithMessage);

    // Node 2 handles message from Node 1
    MessagePacketHandler messagePacketHandler2 =
        new MessagePacketHandler(NodeRecordFactory.DEFAULT);
    Envelope envelopeAt2From1WithMessage = new Envelope();
    Packet<?> pongPacketFrom1To2 = outgoing1Packets.poll(1, TimeUnit.SECONDS);
    envelopeAt2From1WithMessage.put(PACKET_MESSAGE, pongPacketFrom1To2);
    envelopeAt2From1WithMessage.put(SESSION, nodeSessionAt2For1);
    messagePacketHandler2.handle(envelopeAt2From1WithMessage);
    assertNull(envelopeAt2From1WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt2From1WithMessage.get(MESSAGE));
  }

  private OrdinaryMessagePacket createPingPacket(
      Bytes12 authTag, NodeSession session, Bytes requestId) {

    PingMessage pingMessage = createPing(session, requestId);
    Header<OrdinaryAuthData> header = Header
        .createOrdinaryHeader(session.getHomeNodeId(), authTag);
    return OrdinaryMessagePacket
        .create(Bytes16.random(rnd), header, pingMessage, session.getInitiatorKey());
  }

  private static PingMessage createPing(NodeSession session, Bytes requestId) {
    return new PingMessage(requestId, session.getNodeRecord().orElseThrow().getSeq());
  }
}
