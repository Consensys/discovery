/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.info;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.task.TaskStatus;

/** Stores info related to performed request */
public class RequestInfo {

  public static RequestInfo create(Bytes requestId, Request request) {
    return new RequestInfo(TaskStatus.AWAIT, requestId, request);
  }

  private final Bytes requestId;
  private final Request request;
  private TaskStatus taskStatus;
  private V5Message message;

  private RequestInfo(TaskStatus taskStatus, Bytes requestId, Request request) {
    this.taskStatus = taskStatus;
    this.requestId = requestId;
    this.request = request;
  }

  public synchronized TaskStatus getTaskStatus() {
    return taskStatus;
  }

  public synchronized void setTaskStatus(TaskStatus taskStatus) {
    this.taskStatus = taskStatus;
  }

  public synchronized V5Message getMessage() {
    if (message == null) {
      message = getRequest().getRequestMessageFactory().apply(getRequestId());
    }
    return message;
  }

  public Bytes getRequestId() {
    return requestId;
  }

  public Request getRequest() {
    return request;
  }

  @Override
  public synchronized String toString() {
    return "GeneralRequestInfo{"
        + "taskStatus="
        + taskStatus
        + ", requestId="
        + requestId
        + ", requestMessage="
        + getMessage()
        + ", request="
        + request
        + '}';
  }
}
