/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;

/**
 * Storage for nodes, K-Bucket. Holds only {@link #K} nodes, replacing nodes with the same nodeId
 * and nodes with old lastRetry. Also throws out DEAD nodes without taking any notice on other
 * fields.
 */
public class NodeBucket {
  /** Bucket size, number of nodes */
  public static final int K = 16;

  @SuppressWarnings("UnnecessaryLambda")
  private static final Predicate<NodeRecordInfo> FILTER =
      nodeRecord -> nodeRecord.getStatus().equals(NodeStatus.ACTIVE);

  private final TreeSet<NodeRecordInfo> bucket =
      new TreeSet<>((o1, o2) -> o2.getNode().hashCode() - o1.getNode().hashCode());

  public synchronized boolean put(NodeRecordInfo nodeRecord) {
    if (FILTER.test(nodeRecord)) {
      if (!bucket.contains(nodeRecord)) {
        boolean modified = bucket.add(nodeRecord);
        if (bucket.size() > K) {
          NodeRecordInfo worst = null;
          for (NodeRecordInfo nodeRecordInfo : bucket) {
            if (worst == null) {
              worst = nodeRecordInfo;
            } else if (worst.getLastRetry() > nodeRecordInfo.getLastRetry()) {
              worst = nodeRecordInfo;
            }
          }
          bucket.remove(worst);
        }
        return modified;
      } else {
        NodeRecordInfo bucketNode = bucket.subSet(nodeRecord, true, nodeRecord, true).first();
        if (nodeRecord.getLastRetry() > bucketNode.getLastRetry()) {
          bucket.remove(bucketNode);
          bucket.add(nodeRecord);
          return true;
        }
      }
    } else {
      return bucket.remove(nodeRecord);
    }

    return false;
  }

  public synchronized boolean contains(NodeRecordInfo nodeRecordInfo) {
    return bucket.contains(nodeRecordInfo);
  }

  public int size() {
    return bucket.size();
  }

  public synchronized List<NodeRecordInfo> getNodeRecords() {
    return new ArrayList<>(bucket);
  }
}
