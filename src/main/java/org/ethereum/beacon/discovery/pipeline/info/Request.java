/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;

public class Request<TResponse> {
  private final CompletableFuture<TResponse> resultPromise;
  private final Function<Bytes, V5Message> requestMessageFactory;
  private final MultiPacketResponseHandler<?> responseHandler;

  public Request(
      CompletableFuture<TResponse> resultPromise,
      Function<Bytes, V5Message> requestMessageFactory,
      MultiPacketResponseHandler<?> responseHandler) {
    this.resultPromise = resultPromise;
    this.requestMessageFactory = requestMessageFactory;
    this.responseHandler = responseHandler;
  }

  public CompletableFuture<TResponse> getResultPromise() {
    return resultPromise;
  }

  public Function<Bytes, V5Message> getRequestMessageFactory() {
    return requestMessageFactory;
  }

  public MultiPacketResponseHandler<?> getResponseHandler() {
    return responseHandler;
  }
}
