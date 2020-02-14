/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.reactivestreams.Publisher;

/** Discovery server which listens to incoming messages according to setup */
public interface DiscoveryServer {
  void start(Scheduler scheduler);

  void stop();

  /** Raw incoming packets stream */
  Publisher<Envelope> getIncomingPackets();
}
