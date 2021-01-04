/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static java.util.Collections.singletonList;
import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.ethereum.beacon.discovery.TestUtil.TEST_TRAFFIC_READ_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServerImpl;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucket;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

@SuppressWarnings({"DoubleBraceInitialization"})
public class DiscoveryNetworkTest {
  @Test
  public void test() throws Exception {
    // 1) start 2 nodes
    NodeInfo nodePair1 = TestUtil.generateNode(30303);
    NodeInfo nodePair2 = TestUtil.generateNode(30304);
    NodeRecord nodeRecord1 = nodePair1.getNodeRecord();
    NodeRecord nodeRecord2 = nodePair2.getNodeRecord();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database1 = Database.inMemoryDB();
    Database database2 = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage1 =
        nodeTableStorageFactory.createTable(
            database1,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord1,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(nodeRecord2);
                  }
                });
    NodeBucketStorage nodeBucketStorage1 =
        nodeTableStorageFactory.createBucketStorage(database1, TEST_SERIALIZER, nodeRecord1);
    NodeTableStorage nodeTableStorage2 =
        nodeTableStorageFactory.createTable(
            database2,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord2,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(nodeRecord1);
                  }
                });
    NodeBucketStorage nodeBucketStorage2 =
        nodeTableStorageFactory.createBucketStorage(database2, TEST_SERIALIZER, nodeRecord2);
    ExpirationSchedulerFactory expirationSchedulerFactory =
        new ExpirationSchedulerFactory(Executors.newSingleThreadScheduledExecutor());
    DiscoveryManagerImpl discoveryManager1 =
        new DiscoveryManagerImpl(
            new NettyDiscoveryServerImpl(nodeRecord1.getUdpAddress().get(), TEST_TRAFFIC_READ_LIMIT),
            nodeTableStorage1.get(),
            nodeBucketStorage1,
            new LocalNodeRecordStore(
                nodeRecord1, nodePair1.getPrivateKey(), NodeRecordListener.NOOP),
            nodePair1.getPrivateKey(),
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            Schedulers.createDefault().newSingleThreadDaemon("tasks-1"),
            expirationSchedulerFactory,
            TalkHandler.NOOP);
    DiscoveryManagerImpl discoveryManager2 =
        new DiscoveryManagerImpl(
            new NettyDiscoveryServerImpl(nodeRecord2.getUdpAddress().get(), TEST_TRAFFIC_READ_LIMIT),
            nodeTableStorage2.get(),
            nodeBucketStorage2,
            new LocalNodeRecordStore(
                nodeRecord2, nodePair2.getPrivateKey(), NodeRecordListener.NOOP),
            nodePair2.getPrivateKey(),
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            Schedulers.createDefault().newSingleThreadDaemon("tasks-2"),
            expirationSchedulerFactory,
            TalkHandler.NOOP);

    // 3) Expect standard 1 => 2 dialog
    CountDownLatch randomSent1to2 = new CountDownLatch(1);
    CountDownLatch whoareyouSent2to1 = new CountDownLatch(1);
    CountDownLatch authPacketSent1to2 = new CountDownLatch(1);
    CountDownLatch nodesSent2to1 = new CountDownLatch(1);

    Flux.from(discoveryManager1.getOutgoingMessages())
        .map(p -> p.getPacket().demaskPacket(nodeRecord2.getNodeId()))
        .subscribe(
            networkPacket -> {
              // 1 -> 2 random
              if (randomSent1to2.getCount() != 0) {
                assertTrue(networkPacket instanceof OrdinaryMessagePacket);
                System.out.println("1 => 2: " + networkPacket);
                randomSent1to2.countDown();
              } else if (authPacketSent1to2.getCount() != 0) {
                // 1 -> 2 auth packet with FINDNODES
                assertTrue(networkPacket instanceof HandshakeMessagePacket);
                System.out.println("1 => 2: " + networkPacket);
                authPacketSent1to2.countDown();
              } else {
                throw new RuntimeException("Not expected!");
              }
            });
    Flux.from(discoveryManager2.getOutgoingMessages())
        .map(p -> p.getPacket().demaskPacket(nodeRecord1.getNodeId()))
        .subscribe(
            networkPacket -> {
              // 2 -> 1 whoareyou
              if (whoareyouSent2to1.getCount() != 0) {
                assertTrue(networkPacket instanceof WhoAreYouPacket);
                System.out.println("2 => 1: " + networkPacket);
                whoareyouSent2to1.countDown();
              } else {
                // 2 -> 1 nodes
                assertTrue(networkPacket instanceof OrdinaryMessagePacket);
                System.out.println("2 => 1: " + networkPacket);
                nodesSent2to1.countDown();
              }
            });

    // 4) fire 1 to 2 dialog
    discoveryManager2.start();
    discoveryManager1.start();
    discoveryManager1.findNodes(nodeRecord2, singletonList(0));

    assertTrue(randomSent1to2.await(1, TimeUnit.SECONDS));
    assertTrue(whoareyouSent2to1.await(1, TimeUnit.SECONDS));
    int distance1To2 = Functions.logDistance(nodeRecord1.getNodeId(), nodeRecord2.getNodeId());
    assertFalse(nodeBucketStorage1.get(distance1To2).isPresent());
    assertTrue(authPacketSent1to2.await(1, TimeUnit.SECONDS));
    assertTrue(nodesSent2to1.await(1, TimeUnit.SECONDS));
    Thread.sleep(50);
    // 1 sent findnodes to 2, received only (2) in answer, because 3 is not checked
    // 1 added 2 to its nodeBuckets, because its now checked, but not before
    NodeBucket bucketAt1With2 = nodeBucketStorage1.get(distance1To2).get();
    assertEquals(1, bucketAt1With2.size());
    assertEquals(
        nodeRecord2.getNodeId(), bucketAt1With2.getNodeRecords().get(0).getNode().getNodeId());
  }

  // TODO: discovery tasks are emitted from time to time as they should
}
