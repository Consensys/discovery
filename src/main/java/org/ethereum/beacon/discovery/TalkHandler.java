/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/** The application side TALKREQ messages handler */
public interface TalkHandler {

  /** Acts like as the application doesn't support any TALK protocols */
  TalkHandler NOOP = (a, b, c) -> CompletableFuture.completedFuture(Bytes.EMPTY);

  /**
   * return empty bytes if the application doesn't support the protocol. Response bytes otherwise
   */
  CompletableFuture<Bytes> talk(NodeRecord srcNode, Bytes protocol, Bytes request);
}
