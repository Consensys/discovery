/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.ethereum.beacon.discovery.TestManagerWrapper.TestMessage;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.junit.jupiter.api.Test;

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
    m1.getDiscoveryManager().ping(m2.getNodeRecord());

    TestMessage out1_2 = m1.nextOutbound();
    m2_1.deliver(out1_2); // sending Ping 1 => 2

    TestMessage out2_1_1 = m2_1.nextOutbound(); // should be WHoAreYou
    assertThat(out2_1_1.getPacket()).isInstanceOf(WhoAreYouPacket.class);

    m1.getDiscoveryManager().ping(m2.getNodeRecord());

    TestMessage out1_3 = m1.nextOutbound();
    m2_1.deliver(out1_3); // instead of 1 => 2 Handshake sending an unathorized Ping again

    TestMessage out2_1_2 = m2_1.nextOutbound(); // expecting that 2 responds with WhoAreYou again
    assertThat(out2_1_2.getPacket()).isInstanceOf(WhoAreYouPacket.class);
    m1.deliver(out2_1_2);

    m1.exchangeAll(m2_1);

    // should be ok now
    CompletableFuture<Void> pingRes2 = m1.getDiscoveryManager().ping(m2.getNodeRecord());
    m1.exchangeAll(m2_1);
    assertThat(pingRes2).isCompleted();
  }
}
