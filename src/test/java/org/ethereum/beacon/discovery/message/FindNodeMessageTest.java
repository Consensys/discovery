/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.ethereum.beacon.discovery.TestUtil.assertRejectTrailingBytes;
import static org.ethereum.beacon.discovery.TestUtil.assertRoundTrip;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Test;

class FindNodeMessageTest {
  private static final DiscoveryV5MessageDecoder DECODER =
      new DiscoveryV5MessageDecoder(new NodeRecordFactory(new SimpleIdentitySchemaInterpreter()));
  private static final FindNodeMessage MESSAGE =
      new FindNodeMessage(Bytes.fromHexString("0x134488556699"), List.of(1, 2, 3, 4, 6, 9, 10));

  @Test
  void shouldRoundTrip() {
    assertRoundTrip(DECODER, MESSAGE);
  }

  @Test
  void shouldRejectTrailingBytes() {
    assertRejectTrailingBytes(DECODER, MESSAGE);
  }
}
