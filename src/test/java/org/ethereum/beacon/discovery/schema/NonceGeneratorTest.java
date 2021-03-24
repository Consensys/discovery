/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.stream.Stream;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.junit.jupiter.api.Test;

public class NonceGeneratorTest {

  private final Random random = new Random(1);
  private final NonceGenerator generator = new NonceGenerator();

  @Test
  void apply_sanityTest() {
    Bytes12 nonce1 = generator.apply(random);
    Bytes12 nonce2 = generator.apply(random);
    assertThat(nonce1).isNotEqualTo(nonce2);
  }

  @Test
  void apply_isRandomEnough() {
    // check there is no spaces filled with zeroes left
    Bytes12 nonce1 = generator.apply(random);
    byte[] bytes = nonce1.toArrayUnsafe();
    int zeroCount = 0;
    for (byte b : bytes) {
      zeroCount += b == 0 ? 1 : 0;
    }
    assertThat(zeroCount).isLessThan(4);
  }

  @Test
  void apply_noDuplicatesForSomeNonces() {
    assertThat(Stream.generate(() -> generator.apply(random)).limit(10000).distinct())
        .hasSize(10000);
  }
}
