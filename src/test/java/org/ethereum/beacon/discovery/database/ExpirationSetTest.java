/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import org.junit.jupiter.api.Test;

public class ExpirationSetTest {
  private final Clock clock = mock(Clock.class);

  @Test
  public void shouldNotExceedMaximumSize() {
    when(clock.millis()).thenReturn(45L);
    ExpirationSet<String> expirationSet = new ExpirationSet<>(1, clock, 3);
    expirationSet.add("a");
    expirationSet.add("b");
    expirationSet.add("c");
    expirationSet.add("d");
    assertThat(expirationSet.contains("a")).isFalse();
    assertThat(expirationSet.contains("b")).isTrue();
    assertThat(expirationSet.contains("c")).isTrue();
    assertThat(expirationSet.contains("d")).isTrue();
  }

  @Test
  public void shouldExpireOldElements() {
    when(clock.millis()).thenReturn(45L);
    ExpirationSet<String> expirationSet = new ExpirationSet<>(5, clock, 3);
    expirationSet.add("a");
    expirationSet.add("b");
    assertThat(expirationSet.contains("a")).isTrue();
    assertThat(expirationSet.contains("b")).isTrue();
    assertThat(expirationSet.contains("c")).isFalse();
    assertThat(expirationSet.contains("d")).isFalse();
    when(clock.millis()).thenReturn(52L);
    expirationSet.add("c");
    assertThat(expirationSet.contains("a")).isFalse();
    assertThat(expirationSet.contains("b")).isFalse();
    assertThat(expirationSet.contains("c")).isTrue();
    assertThat(expirationSet.contains("d")).isFalse();
    when(clock.millis()).thenReturn(55L);
    expirationSet.add("d");
    assertThat(expirationSet.contains("a")).isFalse();
    assertThat(expirationSet.contains("b")).isFalse();
    assertThat(expirationSet.contains("c")).isTrue();
    assertThat(expirationSet.contains("d")).isTrue();
  }

  @Test
  public void shouldIgnoreReaddedElements() {
    when(clock.millis()).thenReturn(45L);
    ExpirationSet<String> expirationSet = new ExpirationSet<>(5, clock, 3);
    expirationSet.add("a");
    when(clock.millis()).thenReturn(49L);
    expirationSet.add("a");
    assertThat(expirationSet.contains("a")).isTrue();
    when(clock.millis()).thenReturn(51L);
    assertThat(expirationSet.contains("a")).isFalse();
  }
}
