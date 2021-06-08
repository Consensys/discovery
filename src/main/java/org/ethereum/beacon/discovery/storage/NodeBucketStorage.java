/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.stream.Stream;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/** Stores {@link NodeRecordInfo}'s in {@link NodeBucket}'s */
public interface NodeBucketStorage {
  Stream<NodeRecordInfo> getNodeRecords(int index);

  void put(NodeRecordInfo nodeRecordInfo);
}
