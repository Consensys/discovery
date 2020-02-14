/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.info;

import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;

public class GeneralRequestInfo implements RequestInfo {
  private final TaskType taskType;
  private final TaskStatus taskStatus;
  private final Bytes requestId;
  private final CompletableFuture<Void> future;

  public GeneralRequestInfo(
      TaskType taskType, TaskStatus taskStatus, Bytes requestId, CompletableFuture<Void> future) {
    this.taskType = taskType;
    this.taskStatus = taskStatus;
    this.requestId = requestId;
    this.future = future;
  }

  @Override
  public TaskType getTaskType() {
    return taskType;
  }

  @Override
  public TaskStatus getTaskStatus() {
    return taskStatus;
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  @Override
  public CompletableFuture<Void> getFuture() {
    return future;
  }

  @Override
  public RequestInfo withStatus(final TaskStatus status) {
    return new GeneralRequestInfo(getTaskType(), status, getRequestId(), getFuture());
  }

  @Override
  public String toString() {
    return "GeneralRequestInfo{"
        + "taskType="
        + taskType
        + ", taskStatus="
        + taskStatus
        + ", requestId="
        + requestId
        + '}';
  }
}
