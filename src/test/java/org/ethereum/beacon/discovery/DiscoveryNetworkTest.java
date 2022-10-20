/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.ethereum.beacon.discovery.TestUtil.TEST_TRAFFIC_READ_LIMIT;
import static org.ethereum.beacon.discovery.TestUtil.waitFor;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.message.handler.ExternalAddressSelector;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServerImpl;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class DiscoveryNetworkTest {
  @Test
  public void test() throws Exception {
    final Clock clock = Clock.systemUTC();
    // 1) start 2 nodes
    // note: not using port 30303 because of binding issues with macOS.
    NodeInfo nodePair1 = TestUtil.generateNode(30304);
    NodeInfo nodePair2 = TestUtil.generateNode(30305);
    NodeRecord nodeRecord1 = nodePair1.getNodeRecord();
    NodeRecord nodeRecord2 = nodePair2.getNodeRecord();
    LivenessChecker livenessChecker1 = new LivenessChecker(clock);
    LivenessChecker livenessChecker2 = new LivenessChecker(clock);
    KBuckets nodeBucketStorage1 =
        new KBuckets(
            clock,
            new LocalNodeRecordStore(
                nodeRecord1,
                Functions.randomKeyPair().secretKey(),
                NodeRecordListener.NOOP,
                NewAddressHandler.NOOP),
            livenessChecker1);
    KBuckets nodeBucketStorage2 =
        new KBuckets(
            clock,
            new LocalNodeRecordStore(
                nodeRecord2,
                Functions.randomKeyPair().secretKey(),
                NodeRecordListener.NOOP,
                NewAddressHandler.NOOP),
            livenessChecker2);
    ExpirationSchedulerFactory expirationSchedulerFactory =
        new ExpirationSchedulerFactory(Executors.newSingleThreadScheduledExecutor());
    DiscoveryManagerImpl discoveryManager1 =
        new DiscoveryManagerImpl(
            new NettyDiscoveryServerImpl(
                nodeRecord1.getUdpAddress().get(), TEST_TRAFFIC_READ_LIMIT),
            nodeBucketStorage1,
            new LocalNodeRecordStore(
                nodeRecord1,
                nodePair1.getSecretKey(),
                NodeRecordListener.NOOP,
                NewAddressHandler.NOOP),
            nodePair1.getSecretKey(),
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            Schedulers.createDefault().newSingleThreadDaemon("tasks-1"),
            expirationSchedulerFactory,
            TalkHandler.NOOP,
            ExternalAddressSelector.NOOP);
    livenessChecker1.setPinger(discoveryManager1::ping);
    DiscoveryManagerImpl discoveryManager2 =
        new DiscoveryManagerImpl(
            new NettyDiscoveryServerImpl(
                nodeRecord2.getUdpAddress().get(), TEST_TRAFFIC_READ_LIMIT),
            nodeBucketStorage2,
            new LocalNodeRecordStore(
                nodeRecord2,
                nodePair2.getSecretKey(),
                NodeRecordListener.NOOP,
                NewAddressHandler.NOOP),
            nodePair2.getSecretKey(),
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            Schedulers.createDefault().newSingleThreadDaemon("tasks-2"),
            expirationSchedulerFactory,
            TalkHandler.NOOP,
            ExternalAddressSelector.NOOP);
    livenessChecker2.setPinger(discoveryManager2::ping);

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
    assertThat(nodeBucketStorage1.getLiveNodeRecords(distance1To2)).isEmpty();
    assertTrue(authPacketSent1to2.await(1, TimeUnit.SECONDS));
    assertTrue(nodesSent2to1.await(1, TimeUnit.SECONDS));
    // 1 sent findnodes to 2, received only (2) in answer, because 3 is not checked
    // 1 added 2 to its nodeBuckets, because its now checked, but not before
    waitFor(
        () -> {
          Stream<NodeRecord> nodesInBucketAt1With2 =
              nodeBucketStorage1.getLiveNodeRecords(distance1To2);
          assertThat(nodesInBucketAt1With2.map(NodeRecord::getNodeId))
              .containsExactly(nodeRecord2.getNodeId());
        });
  }
}
