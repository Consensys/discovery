/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.schema;

import java.util.Random;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.type.Bytes12;

public class NonceGenerator implements Function<Random, Bytes12> {
  private static final int RANDOM_PART_SIZE = 8;

  private int counterEnd = 0;
  private int counter = 0;
  private Bytes randomPart;

  @Override
  public synchronized Bytes12 apply(Random random) {
    if (counter == counterEnd) {
      randomPart = Bytes.random(RANDOM_PART_SIZE, random);
      counter = random.nextInt();
      counterEnd = counter;
    }
    counter++;
    return Bytes12.wrap(Bytes.wrap(Bytes.ofUnsignedInt(0xFFFFFFFFL & counter), randomPart));
  }
}
