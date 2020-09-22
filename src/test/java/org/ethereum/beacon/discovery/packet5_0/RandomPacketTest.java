/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class RandomPacketTest {

  @Test
  void testEncodeDecode() {
    Bytes32 tag =
        Bytes32.fromHexString("0x1111111111111111111111111111111111111111111111111111111111111111");
    Bytes authTag = Bytes.fromHexString("0x222222222222222222222222");
    Bytes randomData = Bytes.wrap(new byte[RandomPacket.MIN_RANDOM_BYTES]);
    RandomPacket packet1 = RandomPacket.create(tag, authTag, randomData);
    Bytes encodedPacket = packet1.getBytes();

    RandomPacket packet2 = new RandomPacket(encodedPacket);

    assertThat(packet2.getTag()).isEqualTo(tag);
    assertThat(packet2.getAuthTag()).isEqualTo(authTag);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0x01d405f62328003661eb585858", // invalid RLP
        "0x01010101010101010101010101", // list of single bytes
        "0xCC222222222222222222222222", // just a list
      })
  void testInvalidAuthRlpDecode(String authTagRlp) {
    Bytes32 tag =
        Bytes32.fromHexString("0x1111111111111111111111111111111111111111111111111111111111111111");
    Bytes invalidAuthTagBytes = Bytes.fromHexString(authTagRlp);
    Bytes randomData = Bytes.wrap(new byte[44]);

    assertThatThrownBy(
        () -> {
          RandomPacket packet =
              new RandomPacket(Bytes.concatenate(tag, invalidAuthTagBytes, randomData));
          packet.getAuthTag();
        });
  }

  @Test
  void testTooSmallRandomData() {
    Bytes32 tag =
        Bytes32.fromHexString("0x1111111111111111111111111111111111111111111111111111111111111111");
    Bytes authTagBytes = Bytes.fromHexString("0x8C222222222222222222222222");
    Bytes randomData = Bytes.wrap(new byte[43]);

    assertThatThrownBy(
        () -> {
          RandomPacket packet = new RandomPacket(Bytes.concatenate(tag, authTagBytes, randomData));
          packet.getAuthTag();
        });
  }
}
