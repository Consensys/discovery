/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.junit.jupiter.api.Test;

class NodeRecordInfoTest {
  @Test
  public void shouldRoundTripViaRlp() {
    final NodeInfo nodeInfo = TestUtil.generateNode(9000);
    final NodeRecordInfo nodeRecordInfo =
        new NodeRecordInfo(nodeInfo.getNodeRecord(), 4982924L, NodeStatus.SLEEP, 1);

    final Bytes rlp = nodeRecordInfo.toRlpBytes();
    final NodeRecordInfo result = NodeRecordInfo.fromRlpBytes(rlp, NodeRecordFactory.DEFAULT);
    assertEquals(nodeRecordInfo, result);
  }
}
