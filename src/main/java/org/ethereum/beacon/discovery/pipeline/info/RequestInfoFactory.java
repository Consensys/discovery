/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.info;

import static org.ethereum.beacon.discovery.task.TaskStatus.AWAIT;

import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.task.TaskOptions;
import org.ethereum.beacon.discovery.task.TaskType;

public class RequestInfoFactory {
  public static RequestInfo create(
      TaskType taskType, Bytes id, TaskOptions taskOptions, CompletableFuture<Void> future) {
    switch (taskType) {
      case FINDNODE:
        {
          return new FindNodeRequestInfo(AWAIT, id, future, taskOptions.getDistance(), null);
        }
      case PING:
        {
          return new GeneralRequestInfo(taskType, AWAIT, id, future);
        }
      default:
        {
          throw new RuntimeException(
              String.format("Factory doesn't know how to create task with type %s", taskType));
        }
    }
  }
}
