/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.task.DiscoveryTaskManager;

public class DiscoverySystem {
  private final DiscoveryManager discoveryManager;
  private final DiscoveryTaskManager taskManager;
  private final NodeTable nodeTable;

  DiscoverySystem(
      final DiscoveryManager discoveryManager,
      final DiscoveryTaskManager taskManager,
      final NodeTable nodeTable) {
    this.discoveryManager = discoveryManager;
    this.taskManager = taskManager;
    this.nodeTable = nodeTable;
  }

  public CompletableFuture<Void> start() {
    return discoveryManager.start().thenRun(taskManager::start);
  }

  public void stop() {
    taskManager.stop();
    discoveryManager.stop();
  }

  public NodeRecord getLocalNodeRecord() {
    return discoveryManager.getLocalNodeRecord();
  }

  /**
   * Initiates FINDNODE with node `nodeRecord`
   *
   * @param nodeRecord Ethereum Node record
   * @param distance Distance to search for
   * @return Future which is fired when reply is received or fails in timeout/not successful
   *     handshake/bad message exchange.
   */
  public CompletableFuture<Void> findNodes(NodeRecord nodeRecord, int distance) {
    return discoveryManager.findNodes(nodeRecord, distance);
  }

  /**
   * Initiates PING with node `nodeRecord`
   *
   * @param nodeRecord Ethereum Node record
   * @return Future which is fired when reply is received or fails in timeout/not successful
   *     handshake/bad message exchange.
   */
  public CompletableFuture<Void> ping(NodeRecord nodeRecord) {
    return discoveryManager.ping(nodeRecord);
  }

  public Stream<NodeRecordInfo> streamKnownNodes() {
    // 0 indicates no limit to the number of nodes to return.
    return nodeTable.findClosestNodes(Bytes32.ZERO, 0).stream();
  }
}
