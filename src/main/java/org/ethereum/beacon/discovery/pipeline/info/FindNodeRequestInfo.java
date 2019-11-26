/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.info;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;

public class FindNodeRequestInfo extends GeneralRequestInfo {
  private final Integer remainingNodes;
  private final int distance;

  public FindNodeRequestInfo(
      TaskStatus taskStatus,
      Bytes requestId,
      CompletableFuture<Void> future,
      int distance,
      @Nullable Integer remainingNodes) {
    super(TaskType.FINDNODE, taskStatus, requestId, future);
    this.distance = distance;
    this.remainingNodes = remainingNodes;
  }

  public int getDistance() {
    return distance;
  }

  public Integer getRemainingNodes() {
    return remainingNodes;
  }

  @Override
  public String toString() {
    return "FindNodeRequestInfo{"
        + "remainingNodes="
        + remainingNodes
        + ", distance="
        + distance
        + '}';
  }
}
