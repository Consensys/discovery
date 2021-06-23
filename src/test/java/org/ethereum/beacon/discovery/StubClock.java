/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class StubClock extends Clock {

  // Start from something realistic instead of 0.
  private long currentTimeMillis = 1_000_000;

  public void advanceTimeMillis(final long millis) {
    currentTimeMillis += millis;
  }

  @Override
  public ZoneId getZone() {
    return ZoneId.of("UTC");
  }

  @Override
  public Clock withZone(final ZoneId zone) {
    throw new UnsupportedOperationException("Timezone conversion not supported");
  }

  @Override
  public Instant instant() {
    return Instant.ofEpochMilli(currentTimeMillis);
  }
}
