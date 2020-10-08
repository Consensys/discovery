/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.util.Functions.PRIVKEY_SIZE;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestManagerWrapper.TestMessage;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.web3j.crypto.ECKeyPair;

public class DiscoveryManagerTest {
  public static final String LOCALHOST = "127.0.0.1";
  public static final Duration RETRY_TIMEOUT = Duration.ofSeconds(30);
  public static final Duration LIVE_CHECK_INTERVAL = Duration.ofSeconds(30);

  @Test
  public void testRegularHandshake() throws Exception {
    TestNetwork network = new TestNetwork();
    TestManagerWrapper m1 = network.createDiscoveryManager(1);
    TestManagerWrapper m2 = network.createDiscoveryManager(2);

    CompletableFuture<Void> pingRes = m1.getDiscoveryManager().ping(m2.getNodeRecord());

    TestMessage out1_1 = m1.nextOutbound();
    assertThat(out1_1.getPacket()).isInstanceOf(OrdinaryMessagePacket.class);
    m2.deliver(out1_1); // Random (Ping is pending)

    TestMessage out2_1 = m2.nextOutbound();
    assertThat(out2_1.getPacket()).isInstanceOf(WhoAreYouPacket.class);
    m1.deliver(out2_1); // WhoAreYou

    TestMessage out1_2 = m1.nextOutbound();
    assertThat(out1_2.getPacket()).isInstanceOf(HandshakeMessagePacket.class);
    m2.deliver(out1_2); // Handshake + pending Ping

    TestMessage out2_2 = m2.nextOutbound();
    assertThat(out2_2.getPacket()).isInstanceOf(OrdinaryMessagePacket.class);
    m1.deliver(out2_2); // Pong

    assertThat(pingRes).isCompleted();
  }

  @Test
  public void testDroppedSession() throws Exception {
    TestNetwork network = new TestNetwork();
    TestManagerWrapper m1 = network.createDiscoveryManager(1);
    TestManagerWrapper m2 = network.createDiscoveryManager(2);

    CompletableFuture<Void> pingRes1 = m1.getDiscoveryManager().ping(m2.getNodeRecord());

    m1.exchangeAll(m2);

    assertThat(pingRes1).isCompleted();

    // let's say M2 is restarted
    TestManagerWrapper m2_1 = network.createDiscoveryManager(2);

    // ping 1 => 2 would be unathorized on 2
    CompletableFuture<Void> pingRes2 = m1.getDiscoveryManager().ping(m2_1.getNodeRecord());

    // new handshake should be made
    m1.exchangeAll(m2_1);

    assertThat(pingRes2).isCompleted();
  }

  @Test
  public void testDoubleUnathorizedPing() throws Exception {
    TestNetwork network = new TestNetwork();
    TestManagerWrapper m1 = network.createDiscoveryManager(1);
    TestManagerWrapper m2 = network.createDiscoveryManager(2);

    CompletableFuture<Void> pingRes1 = m1.getDiscoveryManager().ping(m2.getNodeRecord());

    m1.exchangeAll(m2);

    assertThat(pingRes1).isCompleted();

    // let's say M2 is restarted
    TestManagerWrapper m2_1 = network.createDiscoveryManager(2);

    // ping 1 => 2 would be unathorized on 2
    CompletableFuture<Void> pingRes2 = m1.getDiscoveryManager().ping(m2.getNodeRecord());

    TestMessage out1_2 = m1.nextOutbound();
    m2_1.deliver(out1_2); // sending Ping 1 => 2

    TestMessage out2_1_1 = m2_1.nextOutbound(); // should be WHoAreYou
    assertThat(out2_1_1.getPacket()).isInstanceOf(WhoAreYouPacket.class);

    CompletableFuture<Void> pingRes3 = m1.getDiscoveryManager().ping(m2.getNodeRecord());

    TestMessage out1_3 = m1.nextOutbound();
    m2_1.deliver(out1_3); // instead of 1 => 2 Handshake sending an unathorized Ping again

    TestMessage out2_1_2 = m2_1.nextOutbound(); // expecting that 2 responds with WhoAreYou again
    assertThat(out2_1_2.getPacket()).isInstanceOf(WhoAreYouPacket.class);
    m1.deliver(out2_1_2);

    m1.exchangeAll(m2);

    Thread.sleep(2000);

    m1.exchangeAll(m2);

    assertThat(pingRes2).isCompleted();
    assertThat(pingRes3).isCompleted();
  }

  private static void deliver(
      DiscoveryManagerImpl from, DiscoveryManagerImpl to, NetworkParcel packet) {
    deliver(from, to, packet.getPacket().getBytes());
  }

  private static void deliver(DiscoveryManagerImpl from, DiscoveryManagerImpl to, Bytes packet) {
    Envelope envelope = new Envelope();
    envelope.put(Field.INCOMING, packet);
    envelope.put(Field.NODE, from.getLocalNodeRecord());
    to.getIncomingPipeline().push(envelope);
  }

  private static DiscoveryManagerImpl createTestManager(int seed) {
    ECKeyPair keyPair = Functions.generateECKeyPair(new Random(seed));
    final Bytes privateKey =
        Bytes.wrap(Utils.extractBytesFromUnsignedBigInt(keyPair.getPrivateKey(), PRIVKEY_SIZE));

    final NodeRecord nodeRecord =
        new NodeRecordBuilder().privateKey(privateKey).address(LOCALHOST, 9000 + seed).build();
    TestDiscoverySystemBuilder builder = new TestDiscoverySystemBuilder();
    builder
        .discoveryServer(Mockito.mock(NettyDiscoveryServer.class))
        .localNodeRecord(nodeRecord)
        .privateKey(privateKey)
        .retryTimeout(RETRY_TIMEOUT)
        .lifeCheckInterval(LIVE_CHECK_INTERVAL);
    DiscoveryManagerImpl mgr = builder.buildDiscoveryManager();
    mgr.getIncomingPipeline().build();
    mgr.getOutgoingPipeline().build();
    return mgr;
  }
}
