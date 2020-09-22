/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet5_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.type.Hashes;
import org.junit.jupiter.api.Test;

class UnknownPacketTest {
  @Test
  void getSourceNodeId_shouldReturnEmptyWhenPacketIsTooShort() {
    final UnknownPacket unknownPacket = new UnknownPacket(Bytes.of(1, 2, 3));
    final Bytes32 destNodeId = Bytes32.ZERO;
    final Bytes destNodeIdHash = Hashes.sha256(destNodeId);
    assertThat(unknownPacket.getSourceNodeId(destNodeId, destNodeIdHash)).isEmpty();
  }

  @Test
  void getSourceNodeId_shouldExtractSourceNodeId() {
    final Bytes messageData = Bytes.wrap(new byte[64]);
    final UnknownPacket unknownPacket = new UnknownPacket(messageData);
    final Bytes32 destNodeId = Bytes32.ZERO;
    final Bytes destNodeIdHash = Hashes.sha256(destNodeId);

    // Should only use first 32 bytes of message data
    final Bytes expectedSourceId = destNodeIdHash.xor(messageData.slice(0, 32));
    assertThat(unknownPacket.getSourceNodeId(destNodeId, destNodeIdHash))
        .contains(expectedSourceId);
  }
}
