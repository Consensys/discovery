/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.RlpDecodeException;
import org.junit.jupiter.api.Test;

class PongMessageTest {

  private final DiscoveryV5MessageDecoder decoder =
      new DiscoveryV5MessageDecoder(NodeRecordFactory.DEFAULT);

  @Test
  void shouldRoundTripIpv4() {
    final PongMessage original =
        new PongMessage(
            Bytes.fromHexString("0x85482293"),
            UInt64.MAX_VALUE.subtract(1),
            Bytes.fromHexString("0x12121212"),
            48);

    final Bytes rlp = original.getBytes();
    final Message result = decoder.decode(rlp);
    assertThat(result).isEqualTo(original);
    assertThat(result.getBytes()).isEqualTo(rlp);
  }

  @Test
  void shouldRoundTripIpv6() {
    final PongMessage original =
        new PongMessage(
            Bytes.fromHexString("0x85482293"),
            UInt64.MAX_VALUE.subtract(1),
            Bytes.fromHexString("0x12121212121212121212121212121212"),
            48);

    final Bytes rlp = original.getBytes();
    final Message result = decoder.decode(rlp);
    assertThat(result).isEqualTo(original);
    assertThat(result.getBytes()).isEqualTo(rlp);
  }

  @Test
  void shouldFailDecodingWhenIpHasIncorrectNumberOfBytes() {
    final PongMessage original =
        new PongMessage(
            Bytes.fromHexString("0x85482293"),
            UInt64.MAX_VALUE.subtract(1),
            Bytes.fromHexString("0x12"),
            48);

    final Bytes rlp = original.getBytes();
    assertThatThrownBy(() -> decoder.decode(rlp)).isInstanceOf(RlpDecodeException.class);
  }
}
