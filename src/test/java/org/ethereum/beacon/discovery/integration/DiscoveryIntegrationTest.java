/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.integration;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.TestUtil.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.BindException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.KeyPair;
import org.apache.tuweni.rlp.RLPReader;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.TalkHandler;
import org.ethereum.beacon.discovery.mock.IdentitySchemaV4InterpreterMock;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DiscoveryIntegrationTest {

  private static final Logger logger = LogManager.getLogger();
  public static final String LOCALHOST = "127.0.0.1";
  public static final Duration RETRY_TIMEOUT = Duration.ofSeconds(30);
  public static final Duration LIVE_CHECK_INTERVAL = Duration.ofSeconds(30);
  public static final Consumer<DiscoverySystemBuilder> NO_MODIFY = __ -> {};
  private static final AtomicInteger nextPort = new AtomicInteger(9001);
  private final List<DiscoverySystem> managers = new ArrayList<>();

  @AfterEach
  public void tearDown() {
    managers.forEach(DiscoverySystem::stop);
  }

  @Test
  public void shouldSuccessfullyCommunicateWithBootnode() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client = createDiscoveryClient(bootnode.getLocalNodeRecord());

    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());

    final CompletableFuture<Collection<NodeRecord>> findNodesResult =
        client.findNodes(bootnode.getLocalNodeRecord(), singletonList(0));
    waitFor(findNodesResult);
    assertTrue(findNodesResult.isDone());
    assertFalse(findNodesResult.isCompletedExceptionally());
  }

  @Test
  public void shouldSuccessfullyCommunicateWithPreviouslyUnknownNode() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client = createDiscoveryClient();

    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());

    final CompletableFuture<Collection<NodeRecord>> findNodesResult =
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
    final CompletableFuture<Collection<NodeRecord>> findNodesResult =
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
          waitFor(node1.searchForNewPeers(), 10);
          waitFor(node2.searchForNewPeers(), 10);
          assertKnownNodes(node2, bootnode, node1);
          assertKnownNodes(node1, bootnode, node2);
          assertKnownNodes(bootnode, node1, node2);
        });
  }

  private void assertKnownNodes(
      final DiscoverySystem source, final DiscoverySystem... expectedNodes) {
    final Set<NodeRecord> actual =
        source
            .streamLiveNodes()
            .filter(record -> !record.equals(source.getLocalNodeRecord()))
            .collect(toSet());
    final Set<NodeRecord> expected =
        Stream.of(expectedNodes).map(DiscoverySystem::getLocalNodeRecord).collect(toSet());

    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  public void shouldHandleNodeChangingSourceAddress() throws Exception {
    final DiscoverySystem node1 = createDiscoveryClient();
    final KeyPair keyPair = Functions.randomKeyPair();
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

    final DiscoverySystem localNode = createDiscoveryClient("0.0.0.0", remoteNodeRecords);

    // Address should have been updated by automatically pinging the bootnodes.
    // Most likely to 127.0.0.1 but it might be something else if the system is configured unusually
    // or uses IPv6 in preference to v4.
    waitFor(
        () -> {
          final InetAddress updatedAddress =
              localNode.getLocalNodeRecord().getUdpAddress().orElseThrow().getAddress();
          assertThat(updatedAddress).isNotEqualTo(InetAddress.getByName("0.0.0.0"));
        });
  }

  @Test
  public void shouldRetrieveNewEnrFromPeerWhenPongReportsItChanged() throws Exception {
    final DiscoverySystem remoteNode = createDiscoveryClient();
    final DiscoverySystem localNode = createDiscoveryClient();

    final NodeRecord originalLocalNodeRecord = localNode.getLocalNodeRecord();
    waitFor(localNode.ping(remoteNode.getLocalNodeRecord()));
    waitFor(remoteNode.ping(localNode.getLocalNodeRecord()));

    // Remote node should have the current local node record
    assertThat(findNodeRecordByNodeId(remoteNode, originalLocalNodeRecord.getNodeId()))
        .contains(originalLocalNodeRecord);

    localNode.updateCustomFieldValue("eth2", Bytes.fromHexString("0x5555"));
    final NodeRecord updatedLocalNodeRecord = localNode.getLocalNodeRecord();

    // Remote node pings us and we report our new seq via PONG so remote should update
    waitFor(remoteNode.ping(localNode.getLocalNodeRecord()));
    waitFor(
        () ->
            assertThat(findNodeRecordByNodeId(remoteNode, originalLocalNodeRecord.getNodeId()))
                .contains(updatedLocalNodeRecord));
  }

  @Test
  public void shouldRetrieveNewEnrFromPeerWhenPingReportsItChanged() throws Exception {
    final DiscoverySystem remoteNode = createDiscoveryClient();
    final DiscoverySystem localNode = createDiscoveryClient();

    final NodeRecord originalLocalNodeRecord = localNode.getLocalNodeRecord();
    final NodeRecord originalRemoteNodeRecord = remoteNode.getLocalNodeRecord();
    waitFor(localNode.ping(remoteNode.getLocalNodeRecord()));
    waitFor(remoteNode.ping(localNode.getLocalNodeRecord()));

    // Remote node should have the current local node record
    assertThat(findNodeRecordByNodeId(remoteNode, originalLocalNodeRecord.getNodeId()))
        .contains(originalLocalNodeRecord);

    // Remote node pings us which should trigger us updating its ENR.
    remoteNode.updateCustomFieldValue("eth2", Bytes.fromHexString("0x5555"));
    final NodeRecord updatedRemoteNodeRecord = remoteNode.getLocalNodeRecord();
    waitFor(remoteNode.ping(localNode.getLocalNodeRecord()));
    waitFor(
        () ->
            assertThat(findNodeRecordByNodeId(localNode, originalRemoteNodeRecord.getNodeId()))
                .contains(updatedRemoteNodeRecord));
  }

  @Test
  public void shouldRetrieveNewEnrFromBootnode() throws Exception {
    final DiscoverySystem remoteNode = createDiscoveryClient();
    final NodeRecord originalRemoteNodeRecord = remoteNode.getLocalNodeRecord();

    remoteNode.updateCustomFieldValue("eth2", Bytes.fromHexString("0x1234"));
    final NodeRecord updatedRemoteNodeRecord = remoteNode.getLocalNodeRecord();
    assertThat(updatedRemoteNodeRecord).isNotEqualTo(originalRemoteNodeRecord);

    final DiscoverySystem localNode = createDiscoveryClient(originalRemoteNodeRecord);
    waitFor(
        () ->
            assertThat(findNodeRecordByNodeId(localNode, originalRemoteNodeRecord.getNodeId()))
                .contains(updatedRemoteNodeRecord));
  }

  private Optional<NodeRecord> findNodeRecordByNodeId(
      final DiscoverySystem searchNode, final Bytes nodeId) {
    return searchNode.streamLiveNodes().filter(node -> node.getNodeId().equals(nodeId)).findAny();
  }

  @Test
  public void checkTalkMessageHandling() throws Exception {
    class TestTalkHandler implements TalkHandler {

      final Executor delayedExecutor =
          CompletableFuture.delayedExecutor(
              200, MILLISECONDS, Executors.newSingleThreadScheduledExecutor());

      NodeRecord srcNode;
      Bytes protocol;

      @Override
      public CompletableFuture<Bytes> talk(NodeRecord srcNode, Bytes protocol, Bytes request) {
        this.srcNode = srcNode;
        this.protocol = protocol;
        return CompletableFuture.supplyAsync(() -> Bytes.wrap(request, request), delayedExecutor);
      }
    }

    TestTalkHandler testTalkHandler = new TestTalkHandler();

    final DiscoverySystem bootnode =
        createDiscoveryClient(
            true, LOCALHOST, Functions.randomKeyPair(), b -> b.talkHandler(testTalkHandler));
    final DiscoverySystem client = createDiscoveryClient(bootnode.getLocalNodeRecord());

    List<CompletableFuture<Bytes>> responses = new ArrayList<>();

    for (int i = 0; i < 16; i++) {
      CompletableFuture<Bytes> resp =
          client.talk(bootnode.getLocalNodeRecord(), Bytes.fromHexString("0xaabbcc"), Bytes.of(i));
      responses.add(resp);
    }

    for (CompletableFuture<Bytes> resp : responses) {
      waitFor(resp);
    }

    assertThat(testTalkHandler.protocol).isEqualTo(Bytes.fromHexString("0xaabbcc"));
    assertThat(testTalkHandler.srcNode.getNodeId())
        .isEqualTo(client.getLocalNodeRecord().getNodeId());
  }

  @Test
  public void shouldRecoverAfterErrorWhileDecodingInboundMessage() throws Exception {
    class BuggyNodeRecordFactory extends NodeRecordFactory {
      public final AtomicBoolean throwError = new AtomicBoolean();

      public BuggyNodeRecordFactory() {
        super(new IdentitySchemaV4Interpreter());
      }

      @Override
      public NodeRecord fromRlp(RLPReader reader) {
        if (throwError.get()) {
          throw new StackOverflowError("test error");
        } else {
          return super.fromRlp(reader);
        }
      }
    }
    BuggyNodeRecordFactory buggyNodeRecordFactory = new BuggyNodeRecordFactory();

    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client =
        createDiscoveryClient(
            true,
            LOCALHOST,
            Functions.randomKeyPair(),
            discoverySystemBuilder ->
                discoverySystemBuilder.nodeRecordFactory(buggyNodeRecordFactory),
            bootnode.getLocalNodeRecord());
    final DiscoverySystem otherClient = createDiscoveryClient(client.getLocalNodeRecord());

    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());

    buggyNodeRecordFactory.throwError.set(true);

    final CompletableFuture<Collection<NodeRecord>> findNodesErrorResult =
        client.findNodes(bootnode.getLocalNodeRecord(), singletonList(0)).orTimeout(3, SECONDS);
    assertThrows(ExecutionException.class, findNodesErrorResult::get);

    buggyNodeRecordFactory.throwError.set(false);

    final CompletableFuture<Void> otherClientPingResult =
        otherClient.ping(client.getLocalNodeRecord());
    waitFor(otherClientPingResult);
    assertTrue(otherClientPingResult.isDone());
    assertFalse(otherClientPingResult.isCompletedExceptionally());

    final CompletableFuture<Collection<NodeRecord>> findNodesResult1 =
        otherClient.findNodes(client.getLocalNodeRecord(), singletonList(0));
    waitFor(findNodesResult1);
    assertTrue(findNodesResult1.isDone());
    assertFalse(findNodesResult1.isCompletedExceptionally());

    final CompletableFuture<Collection<NodeRecord>> findNodesResult2 =
        client.findNodes(bootnode.getLocalNodeRecord(), singletonList(0));
    waitFor(findNodesResult2);
    assertTrue(findNodesResult2.isDone());
    assertFalse(findNodesResult2.isCompletedExceptionally());

    final CompletableFuture<Void> bootnodePingResult = bootnode.ping(client.getLocalNodeRecord());
    waitFor(bootnodePingResult);
    assertTrue(bootnodePingResult.isDone());
    assertFalse(bootnodePingResult.isCompletedExceptionally());
  }

  private DiscoverySystem createDiscoveryClient(final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(true, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final KeyPair keyPair, final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(true, LOCALHOST, keyPair, NO_MODIFY, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final boolean signNodeRecord, final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(
        signNodeRecord, LOCALHOST, Functions.randomKeyPair(), NO_MODIFY, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final String ipAddress, final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(true, ipAddress, Functions.randomKeyPair(), NO_MODIFY, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final boolean signNodeRecord,
      final String ipAddress,
      final KeyPair keyPair,
      final Consumer<DiscoverySystemBuilder> discModifier,
      final NodeRecord... bootnodes)
      throws Exception {

    for (int i = 0; i < 10; i++) {
      int port = nextPort.incrementAndGet();
      final NodeRecordBuilder nodeRecordBuilder = new NodeRecordBuilder();
      if (signNodeRecord) {
        nodeRecordBuilder.secretKey(keyPair.secretKey());
      } else {
        // We're not signing the record so use an identity schema that won't check the
        // signature locally. The other side should still validate it.
        nodeRecordBuilder.nodeRecordFactory(
            new NodeRecordFactory(new IdentitySchemaV4InterpreterMock()));
      }
      final NodeRecord nodeRecord =
          nodeRecordBuilder
              .address(ipAddress, port)
              .publicKey(Functions.deriveCompressedPublicKeyFromPrivate(keyPair.secretKey()))
              .build();
      DiscoverySystemBuilder discoverySystemBuilder =
          new DiscoverySystemBuilder()
              .listen("0.0.0.0", port)
              .localNodeRecord(nodeRecord)
              .secretKey(keyPair.secretKey())
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
}
