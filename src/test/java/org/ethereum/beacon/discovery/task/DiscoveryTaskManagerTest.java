package org.ethereum.beacon.discovery.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.junit.jupiter.api.Test;

public class DiscoveryTaskManagerTest {

  @Test
  void getClosestDistanceShouldReturnThree() {
    for (int distance = KBuckets.MINIMUM_BUCKET; distance <= KBuckets.MAXIMUM_BUCKET; distance++) {
      assertThat(DiscoveryTaskManager.getClosestDistances(distance)).hasSize(3);
    }
  }

  @Test
  void getClosestDistanceShouldRejectTargetBelowMin() {
    assertThatThrownBy(() -> DiscoveryTaskManager.getClosestDistances(KBuckets.MINIMUM_BUCKET - 1))
        .hasMessageContaining("invalid target distance")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getClosestDistanceShouldRejectTargetAboveMax() {
    assertThatThrownBy(() -> DiscoveryTaskManager.getClosestDistances(KBuckets.MAXIMUM_BUCKET + 1))
        .hasMessageContaining("invalid target distance")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getClosestDistanceMiddleTarget() {
    assertThat(DiscoveryTaskManager.getClosestDistances(128)).isEqualTo(List.of(128, 129, 127));
  }

  @Test
  void getClosestDistanceMinTarget() {
    assertThat(DiscoveryTaskManager.getClosestDistances(KBuckets.MINIMUM_BUCKET))
        .isEqualTo(List.of(1, 2, 3));
  }

  @Test
  void getClosestDistanceMaxTarget() {
    assertThat(DiscoveryTaskManager.getClosestDistances(KBuckets.MAXIMUM_BUCKET))
        .isEqualTo(List.of(256, 255, 254));
  }
}
