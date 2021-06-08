/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.Optional;
import java.util.stream.Stream;
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
  private final LocalNodeRecordStore localNodeRecordStore;

  public NodeBucketStorageImpl(LocalNodeRecordStore localNodeRecordStore) {
    this.localNodeRecordStore = localNodeRecordStore;
    this.nodeBucketsTable = new HoleyList<>();
    this.homeNodeId = localNodeRecordStore.getLocalNodeRecord().getNodeId();
  }

  private Optional<NodeBucket> get(int index) {
    return nodeBucketsTable.get(index);
  }

  @Override
  public Stream<NodeRecord> getNodeRecords(final int index) {
    if (index == 0) {
      return Stream.of(localNodeRecordStore.getLocalNodeRecord());
    }
    return get(index).stream()
        .flatMap(bucket -> bucket.getNodeRecords().stream())
        .map(NodeRecordInfo::getNode);
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
