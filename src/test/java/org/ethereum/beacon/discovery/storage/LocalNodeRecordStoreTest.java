/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter.ADDRESS_UPDATER;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.Test;

public class LocalNodeRecordStoreTest {

  @Test
  public void testListenerIsNotCalledWhenSocketAddressHasNotChanged() {
    List<Update> listenerCalls = new ArrayList<>();

    NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes32.leftPad(Bytes.ofUnsignedInt(1)), new InetSocketAddress("127.0.0.1", 9001));

    LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord, null, (o, n) -> listenerCalls.add(new Update(o, n)), ADDRESS_UPDATER);
    assertThat(listenerCalls).isEmpty();

    recordStore.onSocketAddressChanged(nodeRecord.getUdpAddress().orElseThrow());

    // No updates propagated to listener
    assertThat(listenerCalls).isEmpty();

    // Record has not changed
    assertThat(recordStore.getLocalNodeRecord()).isEqualTo(nodeRecord);
  }

  @Test
  void testListenerIsCalledWhenSocketAddressHasChanged() {
    NodeRecord nodeRecord1 =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes32.leftPad(Bytes.ofUnsignedInt(1)), new InetSocketAddress("127.0.0.1", 9001));
    NodeRecord nodeRecord2 =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes32.leftPad(Bytes.ofUnsignedInt(2)), new InetSocketAddress("127.0.0.1", 9002));

    List<Update> listenerCalls = new ArrayList<>();
    LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord1, null, (o, n) -> listenerCalls.add(new Update(o, n)), ADDRESS_UPDATER);
    assertThat(listenerCalls).isEmpty();

    recordStore.onSocketAddressChanged(nodeRecord2.getUdpAddress().orElseThrow());

    // Update was propagated to listener with correct values
    assertThat(listenerCalls).hasSize(1);
    assertThat(listenerCalls.get(0).oldRec).isEqualTo(nodeRecord1);
    assertThat(listenerCalls.get(0).newRec.getUdpAddress())
        .contains(nodeRecord2.getUdpAddress().orElseThrow());

    // Value has been updated on recordStore
    assertThat(recordStore.getLocalNodeRecord().getUdpAddress().orElseThrow())
        .isEqualTo(nodeRecord2.getUdpAddress().get());
  }

  @Test
  void testListenerIsNotCalledWhenCustomFieldValueHasNotChanged() {
    NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
                Bytes32.leftPad(Bytes.ofUnsignedInt(1)), new InetSocketAddress("127.0.0.1", 9001))
            .withUpdatedCustomField("fieldName", Bytes.fromHexString("0x111111"), null);

    List<Update> listenerCalls = new ArrayList<>();
    LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord, null, (o, n) -> listenerCalls.add(new Update(o, n)), ADDRESS_UPDATER);
    assertThat(listenerCalls).isEmpty();

    recordStore.onCustomFieldValueChanged("fieldName", Bytes.fromHexString("0x111111"));

    // No updates propagated to listener
    assertThat(listenerCalls).hasSize(0);

    // Record has not changed
    assertThat(recordStore.getLocalNodeRecord()).isEqualTo(nodeRecord);
  }

  @Test
  void testListenerIsCalledWhenCustomFieldValueHasChanged() {
    NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            Bytes32.leftPad(Bytes.ofUnsignedInt(1)), new InetSocketAddress("127.0.0.1", 9001));

    List<Update> listenerCalls = new ArrayList<>();
    LocalNodeRecordStore recordStore =
        new LocalNodeRecordStore(
            nodeRecord, null, (o, n) -> listenerCalls.add(new Update(o, n)), ADDRESS_UPDATER);
    assertThat(listenerCalls).isEmpty();

    recordStore.onCustomFieldValueChanged("fieldName", Bytes.fromHexString("0x112233"));

    // Update was propagated to listener with correct values
    assertThat(listenerCalls).hasSize(1);
    assertThat(listenerCalls.get(0).oldRec).isEqualTo(nodeRecord);
    assertThat(listenerCalls.get(0).newRec.get("fieldName"))
        .isEqualTo(Bytes.fromHexString("0x112233"));

    // Value has been updated on recordStore
    assertThat(recordStore.getLocalNodeRecord().get("fieldName"))
        .isEqualTo(Bytes.fromHexString("0x112233"));
  }

  private record Update(NodeRecord oldRec, NodeRecord newRec) {}
}
