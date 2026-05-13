/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter.ADDRESS_UPDATER;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.KeyPair;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.crypto.DefaultSigner;
import org.ethereum.beacon.discovery.crypto.Signer;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

public class LocalNodeRecordStoreTest {

  private record Update(NodeRecord oldRecord, NodeRecord newRecord) {}

  @Test
  void testListenerIsCalled() {
    NodeRecord nodeRecord1 =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes.ofUnsignedInt(1), new InetSocketAddress("127.0.0.1", 9001));
    NodeRecord nodeRecord2 =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes.ofUnsignedInt(2), new InetSocketAddress("127.0.0.1", 9002));

    List<Update> listenerCalls = new ArrayList<>();
    LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord1, null, (o, n) -> listenerCalls.add(new Update(o, n)), ADDRESS_UPDATER);
    assertThat(listenerCalls).isEmpty();

    recordStore.onSocketAddressChanged(nodeRecord2.getUdpAddress().orElseThrow());

    assertThat(listenerCalls).hasSize(1);
    assertThat(listenerCalls.get(0).oldRecord()).isEqualTo(nodeRecord1);
    assertThat(listenerCalls.get(0).newRecord().getUdpAddress())
        .contains(nodeRecord2.getUdpAddress().orElseThrow());
    assertThat(recordStore.getLocalNodeRecord().getUdpAddress().orElseThrow())
        .isEqualTo(nodeRecord2.getUdpAddress().get());

    recordStore.onCustomFieldValueChanged("fieldName", Bytes.fromHexString("0x112233"));

    assertThat(listenerCalls).hasSize(2);
    assertThat(listenerCalls.get(1).oldRecord()).isEqualTo(listenerCalls.get(0).newRecord());
    assertThat(listenerCalls.get(1).newRecord().get("fieldName"))
        .isEqualTo(Bytes.fromHexString("0x112233"));
  }

  @Test
  void onBoundPortResolvedUpdatesEphemeralUdpPort() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes.ofUnsignedInt(1), new InetSocketAddress("127.0.0.1", 0));

    final List<NodeRecord> updatedRecords = new ArrayList<>();
    final LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord, null, (o, n) -> updatedRecords.add(n), ADDRESS_UPDATER);

    recordStore.onBoundPortResolved(new InetSocketAddress("0.0.0.0", 54321));

    assertThat(updatedRecords).hasSize(1);
    final InetSocketAddress updatedAddress =
        recordStore.getLocalNodeRecord().getUdpAddress().orElseThrow();
    assertThat(updatedAddress.getPort()).isEqualTo(54321);
    assertThat(updatedAddress.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
  }

  @Test
  void onBoundPortResolvedDoesNotUpdateNonEphemeralPort() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes.ofUnsignedInt(1), new InetSocketAddress("127.0.0.1", 30303));

    final List<NodeRecord> updatedRecords = new ArrayList<>();
    final LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord, null, (o, n) -> updatedRecords.add(n), ADDRESS_UPDATER);

    recordStore.onBoundPortResolved(new InetSocketAddress("0.0.0.0", 54321));

    assertThat(updatedRecords).isEmpty();
    assertThat(recordStore.getLocalNodeRecord()).isEqualTo(nodeRecord);
  }

  /**
   * Verifies that concurrent IPv4 and IPv6 {@code onBoundPortResolved} calls in a dual-stack
   * configuration do not lose either port update. Without serialized writes, the second write would
   * overwrite the first's update leaving one port still at 0.
   */
  @Test
  void onBoundPortResolvedHandlesConcurrentDualStackUpdates() throws Exception {
    final KeyPair keyPair = Functions.randomKeyPair();
    final Signer signer = new DefaultSigner(keyPair.secretKey());

    // Dual-stack record: both IPv4 and IPv6 UDP ports start at 0 (ephemeral).
    final NodeRecord nodeRecord =
        new NodeRecordBuilder()
            .signer(signer)
            .address("127.0.0.1", 0) // udp=0
            .address("::1", 0) // udp6=0
            .build();

    final List<Update> updates = Collections.synchronizedList(new ArrayList<>());
    final LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord,
            signer,
            (o, n) -> updates.add(new Update(o, n)),
            (rec, addr) -> Optional.empty());

    final InetSocketAddress ipv4Bound = new InetSocketAddress("0.0.0.0", 30303);
    final InetSocketAddress ipv6Bound = new InetSocketAddress("::1", 30304);

    runConcurrently(
        () -> recordStore.onBoundPortResolved(ipv4Bound),
        () -> recordStore.onBoundPortResolved(ipv6Bound));

    final NodeRecord finalRecord = recordStore.getLocalNodeRecord();
    assertThat(finalRecord.getUdpAddress().orElseThrow().getPort())
        .as("IPv4 UDP port must be resolved")
        .isEqualTo(30303);
    assertThat(finalRecord.getUdp6Address().orElseThrow().getPort())
        .as("IPv6 UDP port must be resolved")
        .isEqualTo(30304);
    assertThat(updates).hasSize(2);
    assertChainedUpdates(updates, nodeRecord, finalRecord);
  }

  /**
   * Verifies that concurrent custom field updates from issue #190 do not lose either field and keep
   * listener notifications chained.
   */
  @Test
  void onCustomFieldValueChangedHandlesConcurrentUpdates() throws Exception {
    final Signer signer = new DefaultSigner(Functions.randomKeyPair().secretKey());
    final NodeRecord nodeRecord =
        new NodeRecordBuilder().signer(signer).address("127.0.0.1", 30303).build();

    final List<Update> updates = Collections.synchronizedList(new ArrayList<>());
    final LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord, signer, (o, n) -> updates.add(new Update(o, n)), ADDRESS_UPDATER);

    runConcurrently(
        () -> recordStore.onCustomFieldValueChanged("fieldA", Bytes.fromHexString("0xaaaa")),
        () -> recordStore.onCustomFieldValueChanged("fieldB", Bytes.fromHexString("0xbbbb")));

    final NodeRecord finalRecord = recordStore.getLocalNodeRecord();
    assertThat(finalRecord.get("fieldA")).isEqualTo(Bytes.fromHexString("0xaaaa"));
    assertThat(finalRecord.get("fieldB")).isEqualTo(Bytes.fromHexString("0xbbbb"));
    assertThat(updates).hasSize(2);
    assertChainedUpdates(updates, nodeRecord, finalRecord);
  }

  @Test
  void onSocketAddressChangedAndCustomFieldValueChangedHandleConcurrentUpdates() throws Exception {
    final Signer signer = new DefaultSigner(Functions.randomKeyPair().secretKey());
    final NodeRecord nodeRecord =
        new NodeRecordBuilder().signer(signer).address("127.0.0.1", 30303).build();
    final List<Update> updates = Collections.synchronizedList(new ArrayList<>());
    final LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord,
            signer,
            (o, n) -> updates.add(new Update(o, n)),
            (oldRecord, newAddress) ->
                Optional.of(
                    oldRecord.withNewAddress(
                        newAddress, Optional.empty(), Optional.empty(), signer)));
    final InetSocketAddress newAddress = new InetSocketAddress("127.0.0.2", 30304);

    runConcurrently(
        () -> recordStore.onSocketAddressChanged(newAddress),
        () -> recordStore.onCustomFieldValueChanged("fieldA", Bytes.fromHexString("0xaaaa")));

    final NodeRecord finalRecord = recordStore.getLocalNodeRecord();
    final InetSocketAddress finalAddress = finalRecord.getUdpAddress().orElseThrow();
    assertThat(finalAddress.getAddress().getHostAddress()).isEqualTo("127.0.0.2");
    assertThat(finalAddress.getPort()).isEqualTo(30304);
    assertThat(finalRecord.get("fieldA")).isEqualTo(Bytes.fromHexString("0xaaaa"));
    assertThat(updates).hasSize(2);
    assertChainedUpdates(updates, nodeRecord, finalRecord);
  }

  private static void runConcurrently(final CheckedRunnable... tasks) throws Exception {
    final ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
    final CyclicBarrier startBarrier = new CyclicBarrier(tasks.length);
    try {
      final List<Future<?>> futures = new ArrayList<>();
      for (CheckedRunnable task : tasks) {
        futures.add(
            executor.submit(
                () -> {
                  awaitBarrier(startBarrier);
                  task.run();
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(5, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static void awaitBarrier(final CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (BrokenBarrierException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private static void assertChainedUpdates(
      final List<Update> updates, final NodeRecord initialRecord, final NodeRecord finalRecord) {
    NodeRecord expectedOldRecord = initialRecord;
    for (Update update : updates) {
      assertThat(update.oldRecord()).isEqualTo(expectedOldRecord);
      expectedOldRecord = update.newRecord();
    }
    assertThat(expectedOldRecord).isEqualTo(finalRecord);
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
