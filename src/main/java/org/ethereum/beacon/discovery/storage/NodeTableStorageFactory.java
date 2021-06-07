/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/** Creates {@link NodeTableStorage} */
public interface NodeTableStorageFactory {
  /**
   * Creates storage for nodes table
   *
   * @param homeNodeProvider Home node provider, accepts old sequence number of home node, usually
   *     sequence number is increased by 1 on each restart and ENR is signed with new sequence
   *     number
   * @param bootNodesSupplier boot nodes provider
   * @return {@link NodeTableStorage} from `database` but if it doesn't exist, creates new one with
   *     home node provided by `homeNodeSupplier` and boot nodes provided with `bootNodesSupplier`.
   *     Uses `serializerFactory` for node records serialization.
   */
  NodeTableStorage createTable(
      Function<UInt64, NodeRecord> homeNodeProvider, Supplier<List<NodeRecord>> bootNodesSupplier);

  NodeBucketStorage createBucketStorage(NodeRecord homeNode);
}
