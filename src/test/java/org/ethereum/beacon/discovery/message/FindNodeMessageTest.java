/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.RlpDecodeException;
import org.junit.jupiter.api.Test;

class FindNodeMessageTest {

  private final DiscoveryV5MessageDecoder decoder =
      new DiscoveryV5MessageDecoder(new NodeRecordFactory(new SimpleIdentitySchemaInterpreter()));

  @Test
  void shouldRoundTripViaRlp() {
    final FindNodeMessage message =
        new FindNodeMessage(Bytes.fromHexString("0x134488556699"), List.of(1, 2, 3, 4, 6, 9, 10));
    final Bytes rlp = message.getBytes();
    final FindNodeMessage result = (FindNodeMessage) decoder.decode(rlp);
    assertThat(result).isEqualTo(message);
  }

  @Test
  void shouldRejectTrailingBytes() {
    final FindNodeMessage message =
        new FindNodeMessage(Bytes.fromHexString("0x134488556699"), List.of(1, 2, 3, 4, 6, 9, 10));
    final Bytes rlp = Bytes.concatenate(message.getBytes(), Bytes.fromHexString("0x1234"));
    assertThatThrownBy(() -> decoder.decode(rlp)).isInstanceOf(RlpDecodeException.class);
  }

  @Test
  void shouldRejectInvalidDistance() {
    final FindNodeMessage message =
        new FindNodeMessage(Bytes.fromHexString("0x134488556699"), List.of(256));
    final Bytes rlp = message.getBytes();
    assertThatThrownBy(() -> decoder.decode(rlp))
        .isInstanceOf(RlpDecodeException.class)
        .hasMessageContaining("Invalid distance");
  }
}
