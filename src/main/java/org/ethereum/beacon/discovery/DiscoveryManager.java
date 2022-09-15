/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.PongData;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/**
 * Discovery Manager, top interface for peer discovery mechanism as described at <a
 * href="https://github.com/ethereum/devp2p/blob/master/discv5/discv5.md">https://github.com/ethereum/devp2p/blob/master/discv5/discv5.md</a>
 */
public interface DiscoveryManager {

  CompletableFuture<Void> start();

  void stop();

  NodeRecord getLocalNodeRecord();

  void updateCustomFieldValue(final String fieldName, final Bytes value);

  /**
   * Initiates FINDNODE with node `nodeRecord`
   *
   * @param nodeRecord Ethereum Node record
   * @param distances Distances to search for
   * @return Future which is fired when reply is received or fails in timeout/not successful
   *     handshake/bad message exchange.
   */
  CompletableFuture<Collection<NodeRecord>> findNodes(
      NodeRecord nodeRecord, List<Integer> distances);

  /**
   * Initiates PING with node `nodeRecord`
   *
   * @param nodeRecord Ethereum Node record
   * @return Future which is fired when reply is received or fails in timeout/not successful
   *     handshake/bad message exchange.
   */
  CompletableFuture<PongData> ping(NodeRecord nodeRecord);

  /** Sends the TALKREQ so the specified {@code node} and returns the TALKRESP promise */
  CompletableFuture<Bytes> talk(NodeRecord nodeRecord, Bytes protocol, Bytes request);

  Stream<NodeRecord> streamActiveSessions();
}
