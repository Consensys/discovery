/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import java.util.concurrent.CompletableFuture;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.reactivestreams.Publisher;

/** Discovery server which listens to incoming messages according to setup */
public interface DiscoveryServer {
  CompletableFuture<?> start();

  void stop();

  /** Raw incoming packets stream */
  Publisher<Envelope> getIncomingPackets();
}
