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
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.DiscoveryManagerBuilder;
import org.ethereum.beacon.discovery.mock.IdentitySchemaV4InterpreterMock;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.javatuples.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

public class DiscoveryIntegrationTest {
  private static final Logger logger = LogManager.getLogger();
  private int nextPort = 9000;
  private List<DiscoveryManager> managers = new ArrayList<>();

  @AfterEach
  public void tearDown() {
    managers.forEach(DiscoveryManager::stop);
  }

  @Test
  public void shouldSuccessfullyCommunicateWithBootnode() throws Exception {
    final DiscoveryManager bootnode = createDiscoveryClient();
    final DiscoveryManager client = createDiscoveryClient(bootnode.getLocalNodeRecord());
    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    waitFor(pingResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());

    final CompletableFuture<Void> findNodesResult =
        client.findNodes(bootnode.getLocalNodeRecord(), 0);
    waitFor(findNodesResult);
    assertTrue(pingResult.isDone());
    assertFalse(pingResult.isCompletedExceptionally());
  }

  @Test
  public void shouldNotSuccessfullyPingBootnodeWhenNodeRecordIsNotSigned() throws Exception {
    final DiscoveryManager bootnode = createDiscoveryClient();
    final DiscoveryManager client = createDiscoveryClient(false, bootnode.getLocalNodeRecord());

    final CompletableFuture<Void> pingResult = client.ping(bootnode.getLocalNodeRecord());
    assertThrows(TimeoutException.class, () -> waitFor(pingResult));
  }

  private DiscoveryManager createDiscoveryClient(final NodeRecord... bootnodes) throws Exception {
    return createDiscoveryClient(true, bootnodes);
  }

  private DiscoveryManager createDiscoveryClient(
      final boolean signNodeRecord, final NodeRecord... bootnodes) throws Exception {
    final ECKeyPair keyPair = Functions.generateECKeyPair();
    final Bytes privateKey =
        Bytes.wrap(Utils.extractBytesFromUnsignedBigInt(keyPair.getPrivateKey(), PRIVKEY_SIZE));

    int maxPort = nextPort + 10;
    for (int port = nextPort++; port < maxPort; port = nextPort++) {
      final NodeRecordFactory nodeRecordFactory =
          signNodeRecord
              ? NodeRecordFactory.DEFAULT
              // We're not signing the record so use an identity schema that won't check the
              // signature locally. The other side should still validate it.
              : new NodeRecordFactory(new IdentitySchemaV4InterpreterMock());
      final NodeRecord nodeRecord =
          nodeRecordFactory.createFromValues(
              UInt64.ONE,
              Pair.with(EnrField.ID, IdentitySchema.V4),
              Pair.with(EnrField.IP_V4, Bytes.fromHexString("0x7F000001")),
              Pair.with(EnrField.UDP_V4, port),
              Pair.with(
                  EnrFieldV4.PKEY_SECP256K1, Functions.derivePublicKeyFromPrivate(privateKey)));
      if (signNodeRecord) {
        nodeRecord.sign(privateKey);
      }
      final DiscoveryManager discoveryManager =
          new DiscoveryManagerBuilder()
              .localNodeRecord(nodeRecord)
              .privateKey(privateKey)
              .bootnodes(bootnodes)
              .build();
      try {
        waitFor(discoveryManager.start());
        managers.add(discoveryManager);
        return discoveryManager;
      } catch (final Exception e) {
        discoveryManager.stop();
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
