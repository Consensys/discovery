/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.List;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/** Creates {@link NodeTableStorage} */
public interface NodeTableStorageFactory {
  /**
   * Creates storage for nodes table
   *
   * @param bootnodes list of boot nodes
   * @return {@link NodeTableStorage} from `database` but if it doesn't exist, creates new one with
   *     home node provided by `homeNodeSupplier` and boot nodes provided with `bootNodesSupplier`.
   *     Uses `serializerFactory` for node records serialization.
   */
  NodeTableStorage createTable(List<NodeRecord> bootnodes);

  NodeBucketStorage createBucketStorage(LocalNodeRecordStore localNodeRecordStore);
}
