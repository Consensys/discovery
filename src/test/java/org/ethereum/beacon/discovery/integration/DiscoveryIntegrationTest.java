/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.integration;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.util.Functions.PRIVKEY_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.BindException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.TalkHandler;
import org.ethereum.beacon.discovery.mock.IdentitySchemaV4InterpreterMock;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

public class DiscoveryIntegrationTest {
  private static final Logger logger = LogManager.getLogger();
  public static final String LOCALHOST = "127.0.0.1";
  public static final Duration RETRY_TIMEOUT = Duration.ofSeconds(30);
  public static final Duration LIVE_CHECK_INTERVAL = Duration.ofSeconds(30);
  public static final Consumer<DiscoverySystemBuilder> NO_MODIFY = __ -> {};
  private int nextPort = 9001;
  private List<DiscoverySystem> managers = new ArrayList<>();

  @AfterEach
  public void tearDown() {
    managers.forEach(DiscoverySystem::stop);
  }

  /** Runs a discovery server for interactive interop testing */
  @Disabled
  @Test
  void runTestServer() throws Exception {
    final ECKeyPair keyPair = Functions.generateECKeyPair();

    final DiscoverySystem node = createDiscoveryClient(true, "188.134.70.1", keyPair, NO_MODIFY);
    System.out.println("Running node: " + node.getLocalNodeRecord());
    System.out.println("Running node: " + node.getLocalNodeRecord().asEnr());

    Thread.sleep(100000000000L);
  }

  /** Runs a discovery client, sends a ping and waits for pong for interactive interop testing */
  @Disabled
  @Test
  public void runTestClient() throws Exception {
    final ECKeyPair keyPair = Functions.generateECKeyPair();

    NodeRecord remote =
        NodeRecordFactory.DEFAULT.fromBase64(
            "-IS4QIrMgVOYuw2mq68f9hFGTlPzJT5pRWIqKTYL93C5xasmfUGUydi2XrjsbxO1MLYGEl1rR5H1iov6gxOyhegW9hYBgmlkgnY0gmlwhLyGRgGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIyo");
    System.out.println("Connecting to: " + remote);
    final DiscoverySystem client =
        createDiscoveryClient(true, "188.134.70.1", keyPair, NO_MODIFY, remote);
    final CompletableFuture<Void> pingResult = client.ping(remote);
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());
  }

  @Test
  public void shouldSuccessfullyCommunicateWithBootnode() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client = createDiscoveryClient(bootnode.getLocalNodeRecord());

    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());

    final CompletableFuture<Void> findNodesResult =
        client.findNodes(bootnode.getLocalNodeRecord(), singletonList(0));
    waitFor(findNodesResult);
    assertTrue(findNodesResult.isDone());
    assertFalse(findNodesResult.isCompletedExceptionally());
  }

  @Test
  public void shouldSuccessfullyUpdateCustomFieldValue() throws Exception {
    final String CUSTOM_FIELD_NAME = "custom_field_name";
    final Bytes CUSTOM_FIELD_VALUE = Bytes.fromHexString("0xdeadbeef");
    final DiscoverySystem bootnode = createDiscoveryClient();
    assertTrue(bootnode.getLocalNodeRecord().isValid());

    bootnode.updateCustomFieldValue(CUSTOM_FIELD_NAME, CUSTOM_FIELD_VALUE);

    assertTrue(bootnode.getLocalNodeRecord().isValid());
    assertEquals(bootnode.getLocalNodeRecord().get(CUSTOM_FIELD_NAME), CUSTOM_FIELD_VALUE);
  }

  @Test
  public void shouldCompleteFindnodesFutureWhenNoNodesAreFound() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client = createDiscoveryClient(bootnode.getLocalNodeRecord());
    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    final Bytes bootnodeId = bootnode.getLocalNodeRecord().getNodeId();
    final Bytes clientNodeId = client.getLocalNodeRecord().getNodeId();
    final int distance = Functions.logDistance(clientNodeId, bootnodeId);
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());

    // Find nodes at a distance we know has no node records to return.
    final CompletableFuture<Void> findNodesResult =
        client.findNodes(
            bootnode.getLocalNodeRecord(), singletonList(distance == 1 ? 2 : distance - 1));
    waitFor(findNodesResult);
    assertTrue(findNodesResult.isDone());
    assertFalse(findNodesResult.isCompletedExceptionally());
  }

  @Test
  public void shouldNotSuccessfullyPingBootnodeWhenNodeRecordIsNotSigned() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client = createDiscoveryClient(false, bootnode.getLocalNodeRecord());

    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    assertThrows(TimeoutException.class, () -> waitFor(pingResult, 5));
  }

  @Test
  public void shouldDiscoverOtherNodes() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem node1 = createDiscoveryClient(bootnode.getLocalNodeRecord());
    final DiscoverySystem node2 = createDiscoveryClient(bootnode.getLocalNodeRecord());

    waitFor(
        () -> {
          waitFor(node1.searchForNewPeers());
          waitFor(node2.searchForNewPeers());
          assertKnownNodes(bootnode, node1, node2);
          assertKnownNodes(node2, bootnode, node1);
          assertKnownNodes(node1, bootnode, node2);
        });
  }

  private void assertKnownNodes(
      final DiscoverySystem source, final DiscoverySystem... expectedNodes) {
    final Set<NodeRecord> actual =
        source
            .streamKnownNodes()
            .map(NodeRecordInfo::getNode)
            .filter(record -> !record.equals(source.getLocalNodeRecord()))
            .collect(toSet());
    final Set<NodeRecord> expected =
        Stream.of(expectedNodes).map(DiscoverySystem::getLocalNodeRecord).collect(toSet());
    assertEquals(expected, actual);
  }

  @Test
  public void shouldHandleNodeChangingSourceAddress() throws Exception {
    final DiscoverySystem node1 = createDiscoveryClient();
    final ECKeyPair keyPair = Functions.generateECKeyPair();
    final DiscoverySystem node2 = createDiscoveryClient(keyPair, node1.getLocalNodeRecord());

    // Communicate on the first port
    waitFor(node2.ping(node1.getLocalNodeRecord()));
    node2.stop();

    // Then with the same node ID but a different port
    final DiscoverySystem node2OnDifferentPort =
        createDiscoveryClient(keyPair, node1.getLocalNodeRecord());
    waitFor(node2OnDifferentPort.ping(node1.getLocalNodeRecord()));
  }

  @Test
  public void shouldUpdateLocalNodeRecord() throws Exception {
    final NodeRecord[] remoteNodeRecords = {
      createDiscoveryClient().getLocalNodeRecord(),
      createDiscoveryClient().getLocalNodeRecord(),
      createDiscoveryClient().getLocalNodeRecord(),
      createDiscoveryClient().getLocalNodeRecord(),
      createDiscoveryClient().getLocalNodeRecord()
    };

    final DiscoverySystem node2 = createDiscoveryClient("0.0.0.0", remoteNodeRecords);

    assertThat(node2.getLocalNodeRecord().getUdpAddress().orElseThrow().getAddress())
        .isEqualTo(InetAddress.getByName("0.0.0.0"));
    for (NodeRecord remoteNodeRecord : remoteNodeRecords) {
      waitFor(node2.ping(remoteNodeRecord));
    }

    // Address should have been updated. Most likely to 127.0.0.1 but it might be something else
    // if the system is configured unusually or uses IPv6 in preference to v4.
    final InetAddress updatedAddress =
        node2.getLocalNodeRecord().getUdpAddress().orElseThrow().getAddress();
    assertThat(updatedAddress).isNotEqualTo(InetAddress.getByName("0.0.0.0"));
  }

  @Test
  public void checkTalkMessageHandling() throws Exception {
    class TestTalkHandler implements TalkHandler {
      final Executor delayedExecutor =
          CompletableFuture.delayedExecutor(
              200, MILLISECONDS, Executors.newSingleThreadScheduledExecutor());

      NodeRecord srcNode;
      String protocol;

      @Override
      public CompletableFuture<Bytes> talk(NodeRecord srcNode, String protocol, Bytes request) {
        this.srcNode = srcNode;
        this.protocol = protocol;
        return CompletableFuture.supplyAsync(() -> Bytes.wrap(request, request), delayedExecutor);
      }
    }

    TestTalkHandler testTalkHandler = new TestTalkHandler();

    final DiscoverySystem bootnode =
        createDiscoveryClient(
            true, LOCALHOST, Functions.generateECKeyPair(), b -> b.talkHandler(testTalkHandler));
    final DiscoverySystem client = createDiscoveryClient(bootnode.getLocalNodeRecord());

    List<CompletableFuture<Bytes>> responses = new ArrayList<>();

    for (int i = 0; i < 16; i++) {
      CompletableFuture<Bytes> resp =
          client.talk(bootnode.getLocalNodeRecord(), "proto1", Bytes.of(i));
      responses.add(resp);
    }

    for (CompletableFuture<Bytes> resp : responses) {
      waitFor(resp);
    }

    assertThat(testTalkHandler.protocol).isEqualTo("proto1");
    assertThat(testTalkHandler.srcNode.getNodeId())
        .isEqualTo(client.getLocalNodeRecord().getNodeId());
  }

  private DiscoverySystem createDiscoveryClient(final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(true, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final ECKeyPair keyPair, final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(true, LOCALHOST, keyPair, NO_MODIFY, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final boolean signNodeRecord, final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(
        signNodeRecord, LOCALHOST, Functions.generateECKeyPair(), NO_MODIFY, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final String ipAddress, final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(
        true, ipAddress, Functions.generateECKeyPair(), NO_MODIFY, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final boolean signNodeRecord,
      final String ipAddress,
      final ECKeyPair keyPair,
      final Consumer<DiscoverySystemBuilder> discModifier,
      final NodeRecord... bootnodes)
      throws Exception {
    final Bytes privateKey =
        Bytes.wrap(Utils.extractBytesFromUnsignedBigInt(keyPair.getPrivateKey(), PRIVKEY_SIZE));

    int maxPort = nextPort + 10;
    for (int port = nextPort++; port < maxPort; port = nextPort++) {
      final NodeRecordBuilder nodeRecordBuilder = new NodeRecordBuilder();
      if (signNodeRecord) {
        nodeRecordBuilder.privateKey(privateKey);
      } else {
        // We're not signing the record so use an identity schema that won't check the
        // signature locally. The other side should still validate it.
        nodeRecordBuilder.nodeRecordFactory(
            new NodeRecordFactory(new IdentitySchemaV4InterpreterMock()));
      }
      final NodeRecord nodeRecord =
          nodeRecordBuilder
              .address(ipAddress, port)
              .publicKey(Functions.derivePublicKeyFromPrivate(privateKey))
              .build();
      DiscoverySystemBuilder discoverySystemBuilder =
          new DiscoverySystemBuilder()
              .listen("0.0.0.0", port)
              .localNodeRecord(nodeRecord)
              .privateKey(privateKey)
              .retryTimeout(RETRY_TIMEOUT)
              .lifeCheckInterval(LIVE_CHECK_INTERVAL)
              .bootnodes(bootnodes);
      discModifier.accept(discoverySystemBuilder);
      final DiscoverySystem discoverySystem = discoverySystemBuilder.build();
      try {
        waitFor(discoverySystem.start());
        managers.add(discoverySystem);
        return discoverySystem;
      } catch (final Exception e) {
        discoverySystem.stop();
        if (e.getCause() instanceof BindException) {
          logger.info("Port conflict detected, retrying with new port", e);
        } else {
          throw e;
        }
      }
    }
    throw new IllegalStateException("Could not find a free port after multiple attempts");
  }

  private void waitFor(final CompletableFuture<?> future) throws Exception {
    waitFor(future, 30);
  }

  private void waitFor(final CompletableFuture<?> future, final int timeout) throws Exception {
    future.get(timeout, TimeUnit.SECONDS);
  }

  private void waitFor(final ThrowingRunnable assertion) throws Exception {
    int attempts = 0;
    while (true) {
      try {
        assertion.run();
        return;
      } catch (Throwable t) {
        if (attempts < 60) {
          attempts++;
          Thread.sleep(1000);
        } else {
          throw t;
        }
      }
    }
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
