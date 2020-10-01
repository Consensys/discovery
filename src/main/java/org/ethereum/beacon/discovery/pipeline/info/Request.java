package org.ethereum.beacon.discovery.pipeline.info;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;

public class Request {
  private final CompletableFuture<Object> resultPromise;
  private final Function<Bytes, V5Message> requestMessageFactory;
  private final MultiPacketResponseHandler<?> responseHandler;

  public Request(
      CompletableFuture<Object> resultPromise,
      Function<Bytes, V5Message> requestMessageFactory,
      MultiPacketResponseHandler<?> responseHandler) {
    this.resultPromise = resultPromise;
    this.requestMessageFactory = requestMessageFactory;
    this.responseHandler = responseHandler;
  }

  public CompletableFuture<Object> getResultPromise() {
    return resultPromise;
  }

  Function<Bytes, V5Message> getRequestMessageFactory() {
    return requestMessageFactory;
  }

  public MultiPacketResponseHandler<?> getResponseHandler() {
    return responseHandler;
  }
}
