/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.BucketStats;

public interface DiscoverySystem {
  CompletableFuture<Void> start();

  void stop();

  NodeRecord getLocalNodeRecord();

  BucketStats getBucketStats();

  void updateCustomFieldValue(final String fieldName, final Bytes value);

  /**
   * Initiates FINDNODE with node `nodeRecord`
   *
   * @param nodeRecord Ethereum Node record
   * @param distances Distances to search for
   * @return Future which is fired when reply is received or fails in timeout/not successful
   *     handshake/bad message exchange. Contains the collection of nodes returned by the peer.
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
  CompletableFuture<Void> ping(NodeRecord nodeRecord);

  /**
   * Initiates TALK with node `nodeRecord`
   *
   * @param nodeRecord Ethereum Node record
   * @return Promise of the node TALK response.
   */
  CompletableFuture<Bytes> talk(NodeRecord nodeRecord, Bytes protocol, Bytes request);

  Stream<NodeRecord> streamLiveNodes();

  CompletableFuture<Collection<NodeRecord>> searchForNewPeers();

  /**
   * Lookup node in locally stored KBuckets by its nodeId. Allows lookup of local node record.
   *
   * @param nodeId NodeId, big endian UInt256 Node ID in bytes
   * @return NodeRecord if any found
   */
  Optional<NodeRecord> lookupNode(final Bytes nodeId);

  /**
   * Gets all the NodeRecords in the routing table, grouped by their bucket
   *
   * @return all the NodeRecords in the routing table, grouped by their bucket
   */
  List<List<NodeRecord>> getNodeRecordBuckets();
}
