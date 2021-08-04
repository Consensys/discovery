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
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
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
}
