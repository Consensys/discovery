/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.info;

import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;

/** Stores info related to performed request */
public interface RequestInfo {
  /** Task type, in execution of which request was created */
  TaskType getTaskType();

  /** Status of corresponding task */
  TaskStatus getTaskStatus();

  /** Id of request */
  Bytes getRequestId();

  /** Future that should be fired when request is fulfilled or cancelled due to errors */
  CompletableFuture<Void> getFuture();

  /** Return a new RequestInfo with the same information as this one but task status changed. */
  RequestInfo withStatus(TaskStatus status);
}
