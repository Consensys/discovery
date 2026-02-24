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
import java.util.concurrent.CyclicBarrier;
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

  @Test
  void testListenerIsCalled() {
    NodeRecord nodeRecord1 =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes.ofUnsignedInt(1), new InetSocketAddress("127.0.0.1", 9001));
    NodeRecord nodeRecord2 =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes.ofUnsignedInt(2), new InetSocketAddress("127.0.0.1", 9002));

    class Update {
      NodeRecord oldRec;
      NodeRecord newRec;

      public Update(NodeRecord oldRec, NodeRecord newRec) {
        this.oldRec = oldRec;
        this.newRec = newRec;
      }
    }
    List<Update> listenerCalls = new ArrayList<>();
    LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord1, null, (o, n) -> listenerCalls.add(new Update(o, n)), ADDRESS_UPDATER);
    assertThat(listenerCalls).isEmpty();

    recordStore.onSocketAddressChanged(nodeRecord2.getUdpAddress().orElseThrow());

    assertThat(listenerCalls).hasSize(1);
    assertThat(listenerCalls.get(0).oldRec).isEqualTo(nodeRecord1);
    assertThat(listenerCalls.get(0).newRec.getUdpAddress())
        .contains(nodeRecord2.getUdpAddress().orElseThrow());
    assertThat(recordStore.getLocalNodeRecord().getUdpAddress().orElseThrow())
        .isEqualTo(nodeRecord2.getUdpAddress().get());

    recordStore.onCustomFieldValueChanged("fieldName", Bytes.fromHexString("0x112233"));

    assertThat(listenerCalls).hasSize(2);
    assertThat(listenerCalls.get(1).oldRec).isEqualTo(listenerCalls.get(0).newRec);
    assertThat(listenerCalls.get(1).newRec.get("fieldName"))
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
   * configuration do not lose either port update. Without the CAS retry loop, the second write
   * would overwrite the first's update leaving one port still at 0.
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

    final List<NodeRecord> updates = Collections.synchronizedList(new ArrayList<>());
    final LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord, signer, (o, n) -> updates.add(n), (rec, addr) -> Optional.empty());

    // Force both threads to call onBoundPortResolved at exactly the same time.
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final InetSocketAddress ipv4Bound = new InetSocketAddress("0.0.0.0", 30303);
    final InetSocketAddress ipv6Bound = new InetSocketAddress("::1", 30304);

    final Thread ipv4Thread =
        new Thread(
            () -> {
              try {
                barrier.await();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              recordStore.onBoundPortResolved(ipv4Bound);
            });

    final Thread ipv6Thread =
        new Thread(
            () -> {
              try {
                barrier.await();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              recordStore.onBoundPortResolved(ipv6Bound);
            });

    ipv4Thread.start();
    ipv6Thread.start();
    ipv4Thread.join();
    ipv6Thread.join();

    final NodeRecord finalRecord = recordStore.getLocalNodeRecord();
    assertThat(finalRecord.getUdpAddress().orElseThrow().getPort())
        .as("IPv4 UDP port must be resolved")
        .isEqualTo(30303);
    assertThat(finalRecord.getUdp6Address().orElseThrow().getPort())
        .as("IPv6 UDP port must be resolved")
        .isEqualTo(30304);
  }
}
