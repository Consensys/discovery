/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.crypto.SECP256K1.KeyPair;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.crypto.DefaultSigner;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.network.NetworkParcelV5;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.util.Functions;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

public class TestManagerWrapper {

  public class TestMessage {

    private final NodeRecord from;
    private final NetworkParcel parcel;

    public TestMessage(NodeRecord from, NetworkParcel parcel) {
      this.from = from;
      this.parcel = parcel;
    }

    public NodeRecord getFrom() {
      return from;
    }

    public RawPacket getRawPacket() {
      return parcel.getPacket();
    }

    public NetworkParcel getParcel() {
      return parcel;
    }

    public Packet<?> getPacket() {
      TestManagerWrapper destNode = testNetwork.find(getParcel().getDestination().getPort());
      return getRawPacket().demaskPacket(destNode.getNodeRecord().getNodeId());
    }
  }

  public static final String LOCALHOST = "127.0.0.1";
  public static final Duration RETRY_TIMEOUT = Duration.ofSeconds(30);
  public static final Duration LIVE_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final TestNetwork testNetwork;
  private final DiscoveryManagerImpl discoveryManager;
  private final KeyPair keyPair;
  private final BlockingQueue<NetworkParcel> outbound = new LinkedBlockingQueue<>();

  public static TestManagerWrapper create(TestNetwork testNetwork, int seed) {
    final KeyPair keyPair = Functions.randomKeyPair(new Random(seed));
    final DiscoveryManagerImpl testManager = createTestManager(keyPair, 9000 + seed);
    return new TestManagerWrapper(testNetwork, testManager, keyPair);
  }

  private TestManagerWrapper(
      TestNetwork testNetwork, DiscoveryManagerImpl discoveryManager, KeyPair keyPair) {
    this.testNetwork = testNetwork;
    this.discoveryManager = discoveryManager;
    this.keyPair = keyPair;
    Flux.from(discoveryManager.getOutgoingMessages()).subscribe(outbound::add);
  }

  public DiscoveryManagerImpl getDiscoveryManager() {
    return discoveryManager;
  }

  public NodeRecord getNodeRecord() {
    return getDiscoveryManager().getLocalNodeRecord();
  }

  public int getPort() {
    return getNodeRecord().getUdpAddress().orElseThrow().getPort();
  }

  /**
   * Polls for the next outbound message
   *
   * @throws RuntimeException if no outbound messages created during 5 seconds
   */
  public TestMessage nextOutbound() throws RuntimeException {
    return maybeNextOutbound(Duration.ofSeconds(5))
        .orElseThrow(() -> new RuntimeException("No outbound messages"));
  }

  public Optional<TestMessage> maybeNextOutbound(Duration timeout) {
    try {
      return Optional.ofNullable(outbound.poll(timeout.toMillis(), TimeUnit.MILLISECONDS))
          .map(parcel -> new TestMessage(getNodeRecord(), parcel));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  public void deliver(TestMessage message) {
    Envelope envelope = new Envelope();
    envelope.put(Field.INCOMING, message.getRawPacket().getBytes());
    envelope.put(Field.NODE, message.getFrom());
    getDiscoveryManager().getIncomingPipeline().push(envelope);
  }

  public List<TestMessage> exchangeAll(TestManagerWrapper other) {
    List<TestMessage> ret = new ArrayList<>();
    while (!(this.outbound.isEmpty() && other.outbound.isEmpty())) {
      while (!this.outbound.isEmpty()) {
        TestMessage msg = this.nextOutbound();
        ret.add(msg);
        other.deliver(msg);
      }
      while (!other.outbound.isEmpty()) {
        TestMessage msg = other.nextOutbound();
        ret.add(msg);
        this.deliver(msg);
      }
    }
    return ret;
  }

  public SecretKey getSecretKey() {
    return keyPair.secretKey();
  }

  public TestMessage createOutbound(RawPacket packet, TestManagerWrapper to) {
    return new TestMessage(
        getNodeRecord(), new NetworkParcelV5(packet, to.getNodeRecord().getUdpAddress().get()));
  }

  private static DiscoveryManagerImpl createTestManager(KeyPair keyPair, int port) {
    final NodeRecord nodeRecord =
        new NodeRecordBuilder()
            .signer(new DefaultSigner(keyPair.secretKey()))
            .address(LOCALHOST, port)
            .build();
    DiscoverySystemBuilder builder = new DiscoverySystemBuilder();
    builder
        .discoveryServer(Mockito.mock(NettyDiscoveryServer.class))
        .localNodeRecord(nodeRecord)
        .signer(new DefaultSigner(keyPair.secretKey()))
        .retryTimeout(RETRY_TIMEOUT)
        .lifeCheckInterval(LIVE_CHECK_INTERVAL);
    DiscoveryManagerImpl mgr = builder.buildDiscoveryManager();
    mgr.getIncomingPipeline().build();
    mgr.getOutgoingPipeline().build();
    return mgr;
  }
}
