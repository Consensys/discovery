/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.TestUtil.assertRejectTrailingBytes;
import static org.ethereum.beacon.discovery.TestUtil.assertRoundTrip;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Test;

class TalkRespMessageTest {
  private static final DiscoveryV5MessageDecoder DECODER =
      new DiscoveryV5MessageDecoder(NodeRecordFactory.DEFAULT);
  private static final TalkRespMessage MESSAGE =
      new TalkRespMessage(Bytes.fromHexString("0x85482293"), Bytes.fromHexString("0x9876543231"));

  @Test
  void shouldRoundTrip() {
    assertRoundTrip(DECODER, MESSAGE);
  }

  @Test
  void shouldRejectTrailingBytes() {
    assertRejectTrailingBytes(DECODER, MESSAGE);
  }
}
