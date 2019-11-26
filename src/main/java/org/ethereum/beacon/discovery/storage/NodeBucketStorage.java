/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.Optional;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/** Stores {@link NodeRecordInfo}'s in {@link NodeBucket}'s */
public interface NodeBucketStorage {
  Optional<NodeBucket> get(int index);

  void put(NodeRecordInfo nodeRecordInfo);

  void commit();
}
