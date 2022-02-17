/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Test;

class PingMessageTest {

  private final DiscoveryV5MessageDecoder decoder =
      new DiscoveryV5MessageDecoder(NodeRecordFactory.DEFAULT);

  @Test
  void shouldRoundTrip() {
    final PingMessage original =
        new PingMessage(Bytes.fromHexString("0x85482293"), UInt64.MAX_VALUE);

    final Bytes rlp = original.getBytes();
    final Message result = decoder.decode(rlp);
    assertThat(result).isEqualTo(original);
    assertThat(result.getBytes()).isEqualTo(rlp);
  }
}
