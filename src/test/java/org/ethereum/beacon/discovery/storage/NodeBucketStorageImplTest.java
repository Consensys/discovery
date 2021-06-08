/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.junit.jupiter.api.Test;

class NodeBucketStorageImplTest {
  private final NodeInfo localNode = TestUtil.generateNode(9244);
  private final LocalNodeRecordStore localNodeRecordStore =
      new LocalNodeRecordStore(
          localNode.getNodeRecord(), Bytes.EMPTY, NodeRecordListener.NOOP, NewAddressHandler.NOOP);

  private final NodeBucketStorage storage = new NodeBucketStorageImpl(localNodeRecordStore);

  @Test
  void shouldReturnLatestLocalNodeWhenDistanceIsZero() {
    assertThat(storage.getNodeRecords(0)).containsExactly(localNode.getNodeRecord());

    // Should return the new record after updates
    localNodeRecordStore.onCustomFieldValueChanged("eth2", Bytes.fromHexString("0x1324"));
    // Sanity check the ENR actually changed
    assertThat(localNodeRecordStore.getLocalNodeRecord()).isNotEqualTo(localNode.getNodeRecord());

    assertThat(storage.getNodeRecords(0))
        .containsExactly(localNodeRecordStore.getLocalNodeRecord());
  }
}
