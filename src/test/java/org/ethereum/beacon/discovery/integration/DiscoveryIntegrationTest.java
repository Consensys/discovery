/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.integration;

import static org.ethereum.beacon.discovery.util.Functions.PRIVKEY_SIZE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.mock.IdentitySchemaV4InterpreterMock;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

public class DiscoveryIntegrationTest {
  private static final Logger logger = LogManager.getLogger();
  private int nextPort = 9000;
  private List<DiscoverySystem> managers = new ArrayList<>();

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

    final CompletableFuture<Void> findNodesResult =
        client.findNodes(bootnode.getLocalNodeRecord(), 0);
    waitFor(findNodesResult);
    assertTrue(findNodesResult.isDone());
    assertFalse(findNodesResult.isCompletedExceptionally());
  }

  @Test
  public void shouldCompleteFindnodesFutureWhenNoNodesAreFound() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client = createDiscoveryClient(bootnode.getLocalNodeRecord());
    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());

    final CompletableFuture<Void> findNodesResult =
        client.findNodes(bootnode.getLocalNodeRecord(), 10);
    waitFor(findNodesResult);
    assertTrue(findNodesResult.isDone());
    assertFalse(findNodesResult.isCompletedExceptionally());
  }

  @Test
  public void shouldNotSuccessfullyPingBootnodeWhenNodeRecordIsNotSigned() throws Exception {
    final DiscoverySystem bootnode = createDiscoveryClient();
    final DiscoverySystem client = createDiscoveryClient(false, bootnode.getLocalNodeRecord());

    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    assertThrows(TimeoutException.class, () -> waitFor(pingResult));
  }

  private DiscoverySystem createDiscoveryClient(final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(true, bootnodes);
  }

  private DiscoverySystem createDiscoveryClient(
      final boolean signNodeRecord, final NodeRecord... bootnodes) throws Exception {
    final ECKeyPair keyPair = Functions.generateECKeyPair();
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
              .address("127.0.0.1", port)
              .publicKey(Functions.derivePublicKeyFromPrivate(privateKey))
              .build();
      final DiscoverySystem discoverySystem =
          new DiscoverySystemBuilder()
              .localNodeRecord(nodeRecord)
              .privateKey(privateKey)
              .bootnodes(bootnodes)
              .build();
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
    future.get(5, TimeUnit.SECONDS);
  }
}
