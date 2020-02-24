/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.mock.DiscoveryManagerNoNetwork;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.RandomPacket;
import org.ethereum.beacon.discovery.packet.UnknownPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.scheduler.SimpleProcessor;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.NodeBucket;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * Discovery test without real network, instead outgoing stream of each peer is connected with
 * incoming of another and vice versa
 */
public class DiscoveryNoNetworkTest {

  @Test
  public void test() throws Exception {
    // 1) start 2 nodes
    NodeInfo nodePair1 = TestUtil.generateUnverifiedNode(30303);
    NodeInfo nodePair2 = TestUtil.generateUnverifiedNode(30304);
    NodeInfo nodePair3 = TestUtil.generateUnverifiedNode(40412);
    NodeRecord nodeRecord1 = nodePair1.getNodeRecord();
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
            database2,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord2,
            () -> List.of(nodeRecord1, nodePair3.getNodeRecord()));
    NodeBucketStorage nodeBucketStorage2 =
        nodeTableStorageFactory.createBucketStorage(database2, TEST_SERIALIZER, nodeRecord2);
    SimpleProcessor<Bytes> from1to2 =
        new SimpleProcessor<>(
            Schedulers.createDefault().newSingleThreadDaemon("from1to2-thread"), "from1to2");
    SimpleProcessor<Bytes> from2to1 =
        new SimpleProcessor<>(
            Schedulers.createDefault().newSingleThreadDaemon("from2to1-thread"), "from2to1");
    DiscoveryManagerNoNetwork discoveryManager1 =
        new DiscoveryManagerNoNetwork(
            nodeTableStorage1.get(),
            nodeBucketStorage1,
            nodeRecord1,
            nodePair1.getPrivateKey(),
            from2to1,
            Schedulers.createDefault().newSingleThreadDaemon("tasks-1"));
    DiscoveryManagerNoNetwork discoveryManager2 =
        new DiscoveryManagerNoNetwork(
            nodeTableStorage2.get(),
            nodeBucketStorage2,
            nodeRecord2,
            nodePair2.getPrivateKey(),
            from1to2,
            Schedulers.createDefault().newSingleThreadDaemon("tasks-2"));

    // 2) Link outgoing of each one with incoming of another
    Flux.from(discoveryManager1.getOutgoingMessages())
        .subscribe(t -> from1to2.onNext(t.getPacket().getBytes()));
    Flux.from(discoveryManager2.getOutgoingMessages())
        .subscribe(t -> from2to1.onNext(t.getPacket().getBytes()));

    // 3) Expect standard 1 => 2 dialog
    CountDownLatch randomSent1to2 = new CountDownLatch(1);
    CountDownLatch whoareyouSent2to1 = new CountDownLatch(1);
    CountDownLatch authPacketSent1to2 = new CountDownLatch(1);
    CountDownLatch nodesSent2to1 = new CountDownLatch(1);
    Flux.from(from1to2)
        .map(UnknownPacket::new)
        .subscribe(
            networkPacket -> {
              // 1 -> 2 random
              if (randomSent1to2.getCount() != 0) {
                RandomPacket randomPacket = networkPacket.getRandomPacket();
                System.out.println("1 => 2: " + randomPacket);
                randomSent1to2.countDown();
              } else if (authPacketSent1to2.getCount() != 0) {
                // 1 -> 2 auth packet with FINDNODES
                AuthHeaderMessagePacket authHeaderMessagePacket =
                    networkPacket.getAuthHeaderMessagePacket();
                System.out.println("1 => 2: " + authHeaderMessagePacket);
                authPacketSent1to2.countDown();
              }
            });
    Flux.from(from2to1)
        .map(UnknownPacket::new)
        .subscribe(
            networkPacket -> {
              // 2 -> 1 whoareyou
              if (whoareyouSent2to1.getCount() != 0) {
                WhoAreYouPacket whoAreYouPacket = networkPacket.getWhoAreYouPacket();
                System.out.println("2 => 1: " + whoAreYouPacket);
                whoareyouSent2to1.countDown();
              } else {
                // 2 -> 1 nodes
                MessagePacket messagePacket = networkPacket.getMessagePacket();
                System.out.println("2 => 1: " + messagePacket);
                nodesSent2to1.countDown();
              }
            });

    // 4) fire 1 to 2 dialog
    discoveryManager2.start();
    discoveryManager1.start();
    discoveryManager1.findNodes(nodeRecord2, 0);

    assertTrue(randomSent1to2.await(1, TimeUnit.SECONDS));
    assertTrue(whoareyouSent2to1.await(1, TimeUnit.SECONDS));
    int distance1To2 = Functions.logDistance(nodeRecord1.getNodeId(), nodeRecord2.getNodeId());
    assertFalse(nodeBucketStorage1.get(distance1To2).isPresent());
    assertTrue(authPacketSent1to2.await(1, TimeUnit.SECONDS));
    assertTrue(nodesSent2to1.await(1, TimeUnit.SECONDS));
    Thread.sleep(50);
    // 1 sent findnodes to 2, received 0 nodes in answer, because 3 is not checked
    // 1 added 2 to its nodeBuckets, because its now checked, but not before
    NodeBucket bucketAt1With2 = nodeBucketStorage1.get(distance1To2).get();
    assertEquals(1, bucketAt1With2.size());
    assertEquals(
        nodeRecord2.getNodeId(), bucketAt1With2.getNodeRecords().get(0).getNode().getNodeId());
  }

  // TODO: discovery tasks are emitted from time to time as they should
}
