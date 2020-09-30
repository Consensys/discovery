package org.ethereum.beacon.discovery;

import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public interface TalkHandler {

  TalkHandler NOOP = (a, b, c) -> CompletableFuture.completedFuture(Bytes.EMPTY);

  CompletableFuture<Bytes> talk(NodeRecord srcNode, String protocol, Bytes request);
}
