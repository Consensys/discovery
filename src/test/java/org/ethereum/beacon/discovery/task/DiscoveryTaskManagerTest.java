/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.junit.jupiter.api.Test;

public class DiscoveryTaskManagerTest {

  @Test
  void lookupDistancesShouldReturnFour() {
    for (int distance = KBuckets.MINIMUM_BUCKET; distance <= KBuckets.MAXIMUM_BUCKET; distance++) {
      assertThat(DiscoveryTaskManager.lookupDistances(distance)).hasSize(4);
    }
  }

  @Test
  void lookupDistancesShouldRejectTargetBelowMin() {
    assertThatThrownBy(() -> DiscoveryTaskManager.lookupDistances(KBuckets.MINIMUM_BUCKET - 1))
        .hasMessageContaining("invalid target distance: 0")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lookupDistancesShouldRejectTargetAboveMax() {
    assertThatThrownBy(() -> DiscoveryTaskManager.lookupDistances(KBuckets.MAXIMUM_BUCKET + 1))
        .hasMessageContaining("invalid target distance: 257")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void lookupDistancesMiddleTarget() {
    assertThat(DiscoveryTaskManager.lookupDistances(128)).isEqualTo(List.of(128, 129, 130, 131));
  }

  @Test
  void lookupDistancesMinTarget() {
    assertThat(DiscoveryTaskManager.lookupDistances(1)).isEqualTo(List.of(1, 2, 3, 4));
  }

  @Test
  void lookupDistancesMaxTarget() {
    assertThat(DiscoveryTaskManager.lookupDistances(256)).isEqualTo(List.of(256, 255, 254, 253));
  }
}
