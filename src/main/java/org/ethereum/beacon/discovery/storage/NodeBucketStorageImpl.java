/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.database.HoleyList;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Stores {@link NodeRecordInfo}'s in {@link NodeBucket}'s calculating index number of bucket as
 * {@link Functions#logDistance(Bytes, Bytes)} from homeNodeId and ignoring index above {@link
 * #MAXIMUM_BUCKET}
 */
public class NodeBucketStorageImpl implements NodeBucketStorage {
  public static final int MAXIMUM_BUCKET = 256;
  private final HoleyList<NodeBucket> nodeBucketsTable;
  private final Bytes homeNodeId;

  public NodeBucketStorageImpl(NodeRecord homeNode) {
    this.nodeBucketsTable = new HoleyList<>();
    this.homeNodeId = homeNode.getNodeId();
    // Save home node
    NodeBucket zero = new NodeBucket();
    zero.put(NodeRecordInfo.createDefault(homeNode));
    nodeBucketsTable.put(0, zero);
  }

  @Override
  public Optional<NodeBucket> get(int index) {
    return nodeBucketsTable.get(index);
  }

  @Override
  public void put(NodeRecordInfo nodeRecordInfo) {
    int logDistance = Functions.logDistance(homeNodeId, nodeRecordInfo.getNode().getNodeId());
    if (logDistance <= MAXIMUM_BUCKET) {
      Optional<NodeBucket> nodeBucketOpt = nodeBucketsTable.get(logDistance);
      if (nodeBucketOpt.isPresent()) {
        NodeBucket nodeBucket = nodeBucketOpt.get();
        boolean updated = nodeBucket.put(nodeRecordInfo);
        if (updated) {
          nodeBucketsTable.put(logDistance, nodeBucket);
        }
      } else {
        NodeBucket nodeBucket = new NodeBucket();
        nodeBucket.put(nodeRecordInfo);
        nodeBucketsTable.put(logDistance, nodeBucket);
      }
    }
  }
}
