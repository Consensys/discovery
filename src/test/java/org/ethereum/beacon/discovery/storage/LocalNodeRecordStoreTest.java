/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.Test;

public class LocalNodeRecordStoreTest {

  @Test
  void testListenerIsCalled() {
    NodeInfo nodePair1 = TestUtil.generateNode(30303);
    NodeInfo nodePair2 = TestUtil.generateNode(30304);
    NodeRecord nodeRecord1 = nodePair1.getNodeRecord();
    NodeRecord nodeRecord2 = nodePair2.getNodeRecord();

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
            nodeRecord1,
            nodePair1.getPrivateKey(),
            (o, n) -> listenerCalls.add(new Update(o, n)),
            (o, n) -> {
              listenerCalls.add(new Update(o, n));
              return Optional.of(n);
            });
    assertThat(listenerCalls).isEmpty();

    recordStore.onSocketAddressChanged(nodeRecord2.getUdpAddress().get());

    assertThat(listenerCalls).hasSize(1);
    assertThat(listenerCalls.get(0).oldRec).isEqualTo(nodeRecord1);
    assertThat(listenerCalls.get(0).newRec.getUdpAddress())
        .contains(nodeRecord2.getTcpAddress().get());

    recordStore.onCustomFieldValueChanged("fieldName", Bytes.fromHexString("0x112233"));

    assertThat(listenerCalls).hasSize(2);
    assertThat(listenerCalls.get(1).oldRec).isEqualTo(listenerCalls.get(0).newRec);
    assertThat(listenerCalls.get(1).newRec.get("fieldName"))
        .isEqualTo(Bytes.fromHexString("0x112233"));
  }
}
