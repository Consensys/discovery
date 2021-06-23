/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.liveness;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class LivenessChecker {
  private static final Logger LOG = LogManager.getLogger();

  static final int MAX_CONCURRENT_PINGS = 3;
  static final int MAX_QUEUE_SIZE = 1000;

  private final Set<NodeRecord> activePings = new HashSet<>();
  private final Set<NodeRecord> queuedPings = new LinkedHashSet<>();

  private final Pinger pinger;

  public LivenessChecker(final Pinger pinger) {
    this.pinger = pinger;
  }

  /**
   * Adds the specified node to the queue of nodes to perform a liveness check on.
   *
   * @param node the node to check liveness
   */
  public synchronized void checkLiveness(NodeRecord node) {
    if (activePings.contains(node)) {
      // Already checking node
      // Note: We don't need to check queuedPings because it's a set so the add just does nothing
      // and if we have capacity to ping immediately the queue must be empty.
      return;
    }
    if (activePings.size() < MAX_CONCURRENT_PINGS) {
      sendPing(node);
    } else if (queuedPings.size() < MAX_QUEUE_SIZE) {
      queuedPings.add(node);
    }
  }

  private void sendPing(final NodeRecord node) {
    queuedPings.remove(node);
    activePings.add(node);
    pinger
        .ping(node)
        .whenComplete(
            (__, error) -> {
              if (error != null) {
                LOG.trace("Liveness check failed for node {}", node, error);
              }
              synchronized (this) {
                activePings.remove(node);
                // Ping the next node in the queue if any.
                queuedPings.stream().findFirst().ifPresent(this::sendPing);
              }
            });
  }

  public interface Pinger {
    CompletableFuture<Void> ping(NodeRecord node);
  }
}
