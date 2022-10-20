/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.TestUtil.assertRejectTrailingBytes;
import static org.ethereum.beacon.discovery.TestUtil.assertRoundTrip;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Test;

class PingMessageTest {
  private static final DiscoveryV5MessageDecoder DECODER =
      new DiscoveryV5MessageDecoder(NodeRecordFactory.DEFAULT);
  private static final PingMessage MESSAGE =
      new PingMessage(Bytes.fromHexString("0x85482293"), UInt64.MAX_VALUE);

  @Test
  void shouldRoundTrip() {
    assertRoundTrip(DECODER, MESSAGE);
  }

  @Test
  void shouldRejectTrailingBytesIpv6() {
    assertRejectTrailingBytes(DECODER, MESSAGE);
  }
}
