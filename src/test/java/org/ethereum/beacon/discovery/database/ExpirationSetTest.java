package org.ethereum.beacon.discovery.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import org.junit.jupiter.api.Test;

public class ExpirationSetTest {
  private final Clock clock = mock(Clock.class);

  @Test
  public void shouldAppreciateSize() {
    when(clock.millis()).thenReturn(12345L);
    ExpirationSet<String> expirationSet = new ExpirationSet<>(1, clock, 3);
    expirationSet.add("a");
    expirationSet.add("b");
    expirationSet.add("c");
    expirationSet.add("d");
    assertThat(expirationSet.size()).isEqualTo(3);
    assertThat(expirationSet.contains("d")).isTrue();
  }

  @Test
  public void oldElementsShouldExpire() {
    when(clock.millis()).thenReturn(12345L);
    ExpirationSet<String> expirationSet = new ExpirationSet<>(5, clock, 3);
    expirationSet.add("a");
    expirationSet.add("b");
    when(clock.millis()).thenReturn(12352L);
    expirationSet.add("c");
    when(clock.millis()).thenReturn(12355L);
    expirationSet.add("d");
    assertThat(expirationSet.size()).isEqualTo(2);
    assertThat(expirationSet.contains("c")).isTrue();
    assertThat(expirationSet.contains("d")).isTrue();
  }

  @Test
  public void addingExistingElementShouldBeIgnore() {
    when(clock.millis()).thenReturn(12345L);
    ExpirationSet<String> expirationSet = new ExpirationSet<>(5, clock, 3);
    expirationSet.add("a");
    when(clock.millis()).thenReturn(12349L);
    expirationSet.add("a");
    assertThat(expirationSet.size()).isEqualTo(1);
    when(clock.millis()).thenReturn(12351L);
    assertThat(expirationSet.size()).isEqualTo(0);
    assertThat(expirationSet.contains("a")).isFalse();
  }
}
